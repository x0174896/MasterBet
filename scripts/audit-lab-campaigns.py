"""Read-only audit of campaign preservation, probabilities, staking and public charts.

Only the documented local PostgreSQL database and localhost HTTP are allowed.
Reports are private; no provider requests, database writes or legacy advancement.
"""
import argparse
import datetime as dt
from decimal import Decimal, ROUND_HALF_UP
import json
import math
import os
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def instant(value):
    return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))


def query(sql):
    config = {}
    for line in (ROOT / 'backend/.env.local').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            config[key.strip()] = value.strip()
    assert re.fullmatch(r'jdbc:postgresql://(127\.0\.0\.1|localhost):5432/masterbet_local', config['DB_URL'])
    assert config['DB_USERNAME'] == 'masterbet_app'
    env = dict(os.environ, PGPASSWORD=config['DB_PASSWORD'], PGCLIENTENCODING='UTF8')
    command = [r'C:\Program Files\PostgreSQL\18\bin\psql.exe', '-X', '-A', '-t', '-q',
               '-h', '127.0.0.1', '-p', '5432', '-U', 'masterbet_app', '-d', 'masterbet_local',
               '--no-password', '-v', 'ON_ERROR_STOP=1']
    result = subprocess.run(command, input='BEGIN READ ONLY;\n' + sql + '\nCOMMIT;',
                            text=True, capture_output=True, encoding='utf-8', env=env, check=True)
    return json.loads(result.stdout, parse_float=Decimal)


def snapshot(reference):
    return query(f"""SELECT json_build_object(
      'sources', json_build_object(
        'matches',(SELECT count(*) FROM lab_match),'quotes',(SELECT count(*) FROM lab_quote),
        'revisions',(SELECT count(*) FROM lab_revision),'alignments',(SELECT count(*) FROM lab_fixture_alignment),
        'checkpoints',(SELECT count(*) FROM lab_download_checkpoint),
        'downloadJobs',(SELECT count(*) FROM lab_job WHERE kind IN ('catalog','results','quotes','reconcile'))),
      'original',(SELECT row_to_json(r) FROM lab_run r WHERE id='{reference}'),
      'betDigest',(SELECT md5(string_agg(id||':'||md5(payload),'' ORDER BY id)) FROM lab_bet WHERE run_id='{reference}'),
      'stepDigest',(SELECT md5(string_agg(step_number||':'||include_cup_scores||':'||md5(payload),''
          ORDER BY step_number,include_cup_scores)) FROM lab_step WHERE run_id='{reference}'),
      'resultDigest',(SELECT md5(string_agg(fixture_id||':'||md5(payload),'' ORDER BY fixture_id))
          FROM lab_run_result WHERE run_id='{reference}'),
      'spoilerDigest',(SELECT md5(string_agg(s.bet_id||':'||s.revealed_at,'' ORDER BY s.bet_id))
          FROM lab_spoiler s JOIN lab_bet b ON b.id=s.bet_id WHERE b.run_id='{reference}'),
      'campaignIdentities',(SELECT md5(string_agg(id||':'||created_at,'' ORDER BY id)) FROM lab_run));
    """)


def result(home, away):
    return '1' if home > away else 'X' if home == away else '2'


def won(quote, score):
    period = quote['market']['period']
    h, a = ((score['homeHt'], score['awayHt']) if period == 'p1' else
            (score['homeFt'] - score['homeHt'], score['awayFt'] - score['awayHt']) if period == 'p2'
            else (score['homeFt'], score['awayFt']))
    kind, outcome = quote['market']['type'], quote['outcome']
    if kind == '1x2': return outcome == result(h, a)
    if kind == 'bothteamsscore': return (h > 0 and a > 0) == (outcome == 'Yes')
    if kind == 'correctscore': return outcome.replace(':', '-') == f'{h}-{a}'
    if kind == 'halftime-fulltime':
        return outcome == result(score['homeHt'], score['awayHt']) + '/' + result(h, a)
    assert kind in ('totals', 'teamtotals-team1', 'teamtotals-team2')
    total = h + a if kind == 'totals' else h if kind.endswith('team1') else a
    return total > quote['market']['line'] if outcome == 'Over' else total < quote['market']['line']


def probability(quote, rates):
    def pmf(mean, n): return math.exp(-mean) * mean ** n / math.factorial(n)
    period, kind, outcome = quote['market']['period'], quote['market']['type'], quote['outcome']
    h, a = rates[:2] if period == 'p1' else rates[2:] if period == 'p2' else (rates[0] + rates[2], rates[1] + rates[3])
    if kind == '1x2': return sum(pmf(h, x) * pmf(a, y) for x in range(65) for y in range(65) if result(x, y) == outcome)
    if kind == 'bothteamsscore':
        p = (1 - math.exp(-h)) * (1 - math.exp(-a))
        return p if outcome == 'Yes' else 1 - p
    if kind == 'correctscore':
        x, y = map(int, outcome.replace(':', '-').split('-'))
        return pmf(h, x) * pmf(a, y)
    if kind == 'halftime-fulltime':
        ht, ft = outcome.split('/')
        difference = {d: sum(pmf(rates[2], x) * pmf(rates[3], y)
                      for x in range(33) for y in range(33) if x-y == d) for d in range(-32, 33)}
        return sum(pmf(rates[0], x) * pmf(rates[1], y) * chance
                   for x in range(33) for y in range(33) for d, chance in difference.items()
                   if result(x, y) == ht and result(x-y+d, 0) == ft)
    assert kind in ('totals', 'teamtotals-team1', 'teamtotals-team2')
    mean = h + a if kind == 'totals' else h if kind.endswith('team1') else a
    p = sum(pmf(mean, n) for n in range(math.floor(quote['market']['line']) + 1))
    return 1-p if outcome == 'Over' else p


def load_run(run_id):
    assert re.fullmatch(r'[A-Za-z0-9:_-]{1,80}', run_id)
    return query(f"""SELECT json_build_object(
      'run',(SELECT row_to_json(r) FROM lab_run r WHERE id='{run_id}'),
      'bets',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_bet WHERE run_id='{run_id}'),
      'steps',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_step WHERE run_id='{run_id}'),
      'results',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_run_result WHERE run_id='{run_id}'),
      'shadows',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_learning_prediction WHERE run_id='{run_id}'),
      'markets',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_market_frame WHERE run_id='{run_id}'));
    """)
def decision_keys(bet, raw=None):
    quote, features = bet['quote'], bet['features']
    ev = float(bet['probability'] if raw is None else raw) * float(quote['price']) - 1
    odds = float(quote['price'])
    keys = {'market:' + quote['market']['type'] + ':' + quote['market']['period'] + ':' + quote['outcome'],
            f"league:{bet['competitionId']}:{bet['season']}",
            'ev:' + ('5-15' if ev < .15 else '15-30' if ev < .30 else '30-plus'),
            'odds:' + ('below-1.5' if odds < 1.5 else '1.5-2.5' if odds < 2.5 else '2.5-plus')}
    if len(features) >= 32:
        if features[21] + features[24] >= 4: keys.add('calendar:busy')
        if features[28] == 1 and features[31] == 1: keys.add('previous:both-ht-zero')
        if features[26] == 1 or features[29] == 1: keys.add('previous:conceded-three')
    if (instant(bet['kickoff']) - instant(quote['observedAt'])).total_seconds() >= 86400:
        keys.add('quote:older-24h')
    return keys


def audit_learning(data, parent, step):
    learning = step.get('learning')
    if not learning: return
    cutoff, cups = instant(step['cutoff']), step['includeCupScores']
    prefix = {}
    for origin, field in ((parent, 'bets'), (data, 'shadows')):
        results = {m['fixture']['id']: m for m in origin['results']}
        for b in origin[field]:
            match = results.get(b['fixtureId'])
            if b['includeCupScores'] != cups or not match: continue
            end, predicted = instant(match['fixture']['endedAt']), instant(b['availableAt'])
            if end < cutoff and predicted < end and predicted < cutoff:
                prefix[b['fixtureId']] = (b, match)
    assert len(prefix) == learning['observations'] == learning['calibration']['support']
    if learning.get('markets'):
        assert learning['latestResult'] == learning['markets']['latestResult']
        return  # Conditional training/validation populations and labels are reconstructed below.
    if prefix:
        assert max(instant(m['fixture']['endedAt']) for _, m in prefix.values()) == instant(learning['latestResult'])
    for rule in learning['rules']:
        cases = [(b, m) for b, m in prefix.values() if rule['key'] in decision_keys(b)]
        assert len(cases) == rule['support']
        if rule['excluded']:
            assert rule['firstSupport'] >= 30 and rule['secondSupport'] >= 30
            assert rule['firstRoi'] < 0 and rule['secondRoi'] < 0 and rule['upperMeanReturn'] < 0
            assert rule['firstRoi'] + Decimal('.02') < rule['comparisonFirstRoi']
            assert rule['secondRoi'] + Decimal('.02') < rule['comparisonSecondRoi']


def audit_run(run_id, api):
    data = load_run(run_id)
    assert data['run']['status'] == 'COMPLETED'
    config = json.loads(data['run']['config'])
    parent = load_run(config['referenceRunId']) if config.get('referenceRunId') else None
    if parent:
        assert data['run']['data_hash'] == parent['run']['data_hash'], 'Source content changed'
        original = json.loads(parent['run']['config'])
        assert all(config[k] == original[k] for k in ('from', 'to', 'competitionIds', 'unitProfit', 'mode'))
        for step in data['steps']: audit_learning(data, parent, step)
    steps = {(s['number'], s['includeCupScores']): s for s in data['steps']}
    matches = {m['fixture']['id']: m for m in data['results']}
    seen = set()
    for b in data['bets'] + data['shadows']:
        key = (b['fixtureId'], b['includeCupScores'], b['modelVersion'] == 'shadow-v1')
        assert key not in seen
        seen.add(key)
        step = steps[(b['step'], b['includeCupScores'])]
        assert instant(b['availableAt']) == instant(step['cutoff']) <= instant(b['kickoff'])
        assert instant(b['quote']['observedAt']) < instant(b['kickoff'])
        model = step['model']
        x = [float(v) for v in b['features']]
        rates = [max(.05, min(6, math.exp(float(w[0]) + sum(float(w[i+1]) *
                 (x[i] - float(model['means'][i])) / float(model['scales'][i]) for i in range(len(x))))))
                 for w in model['weights']]
        p = probability(b['quote'], rates)
        raw = p
        learning = step.get('learning')
        if learning:
            if learning['latestResult']: assert instant(learning['latestResult']) < instant(step['cutoff'])
            for signal in learning['signals']:
                if signal['disabled']:
                    assert float(signal['firstGain']) >= .01 and float(signal['secondGain']) >= .01
                    assert all(float(w[c+1]) == 0 for w in model['weights'] for c in signal['columns'])
            if learning.get('markets') and b['modelVersion'] != 'shadow-v1':
                family = market_family(b['quote'])
                calibration = next(c for c in learning['markets']['families'] if c['family'] == family)
                assert calibration_supports(calibration, raw)
                if calibration['accepted']:
                    p = logistic(float(calibration['offset']) + float(calibration['slope']) * logit(raw))
                rules = next((f['rules'] for f in learning['markets']['conditions'] if f['family'] == family), [])
                assert not decision_keys(b, raw).intersection(r['key'] for r in rules if r['excluded'])
            elif learning['calibration']['accepted'] and b['modelVersion'] != 'shadow-v1':
                p = max(.001, min(.999, p))
                p = 1 / (1 + math.exp(-(math.log(p / (1-p)) + float(learning['calibration']['offset']))))
            if b['modelVersion'] != 'shadow-v1' and not learning.get('markets'):
                assert not decision_keys(b, raw).intersection(r['key'] for r in learning['rules'] if r['excluded'])
        assert abs(p - float(b['probability'])) < 1e-10
        assert abs(float(b['expectedValue']) - (p * float(b['quote']['price']) - 1)) < 1e-10
        assert b['probability'] >= Decimal('.5') and b['expectedValue'] >= Decimal('.05')
        assert b['quote']['price'] <= 5
        assert b['stake'] == max(Decimal('.01'), (b['unitProfit'] / (b['quote']['price'] - 1)).quantize(Decimal('.01'), rounding=ROUND_HALF_UP))
    market_report = audit_markets(data) if data.get('markets') else None
    checked = []
    for cups in (False, True):
        bets = [b for b in data['bets'] if b['includeCupScores'] == cups]
        settled = sorted(bets, key=lambda b: (instant(matches[b['fixtureId']]['fixture']['endedAt']), b['id']))
        balance = Decimal(0)
        expected_chart = []
        for b in settled:
            gain = ((b['stake'] * (b['quote']['price']-1)).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
                    if won(b['quote'], matches[b['fixtureId']]['score']) else -b['stake'])
            balance += gain
            expected_chart.append((b['id'], gain, balance))
        url = api + '/api/lab/workspace?' + urllib.parse.urlencode(dict(runId=run_id, cups=str(cups).lower(), cursor='2026-12-31T23:59:59Z'))
        with urllib.request.urlopen(url, timeout=120) as response:
            public = json.loads(response.read(), parse_float=Decimal)
        assert public['net'] == balance and public['settled'] == len(settled)
        assert len(public['chart']) == len(settled) + 1 and public['chart'][0]['cumulative'] == 0
        assert [(p['betId'], p['profit'], p['cumulative']) for p in public['chart'][1:]] == expected_chart
        checked.append(dict(cups=cups, bets=len(bets), net=str(balance)))
    return dict(run=run_id, steps=len(steps), shadows=len(data['shadows']), modes=checked, marketControls=market_report)


def market_family(quote):
    return ':'.join((quote['market']['type'], quote['market']['period'], quote['outcome']))


def probability_band(p):
    return '50-60' if p < .6 else '60-70' if p < .7 else '70-80' if p < .8 else '80-100'


def logit(p):
    p = max(.001, min(.999, p))
    return math.log(p / (1-p))


def logistic(x):
    return 1 / (1 + math.exp(-x))


def calibration_supports(calibration, p):
    return (float(calibration['minProbability']) - .025 <= p <= float(calibration['maxProbability']) + .025
            and any(b['ready'] and b['band'] == probability_band(p) for b in calibration['bands']))


def fit_market_coefficients(train, prior):
    counts = {}
    for o in train: counts[o['fixture']] = counts.get(o['fixture'], 0) + 1
    a, b = prior
    for _ in range(40):
        ga, gb, aa, ab, bb = 10*(a-prior[0]), 10*(b-prior[1]), 10., 0., 10.
        for o in train:
            weight = 1/counts[o['fixture']]
            x = logit(o['p']); p = logistic(a+b*x)
            residual = weight*(p-o['won']); curvature = weight*p*(1-p)
            ga += residual; gb += residual*x; aa += curvature; ab += curvature*x; bb += curvature*x*x
        determinant = aa*bb-ab*ab
        na = max(-3, min(3, a-(bb*ga-ab*gb)/determinant))
        nb = max(0, min(2, b-(aa*gb-ab*ga)/determinant))
        if abs(na-a)+abs(nb-b) < 1e-9: return na, nb
        a, b = na, nb
    return a, b


def market_loss(rows, offset, slope):
    counts = {}
    for o in rows: counts[o['fixture']] = counts.get(o['fixture'],0)+1
    brier = log_loss = total = 0.
    for o in rows:
        weight = 1/counts[o['fixture']]
        p = max(.001,min(.999,logistic(offset+slope*logit(o['p']))))
        brier += weight*(p-o['won'])**2
        log_loss -= weight*(o['won']*math.log(p)+(1-o['won'])*math.log(1-p))
        total += weight
    return brier/total,log_loss/total


def audit_markets(data):
    frames = data['markets']
    steps = {(s['includeCupScores'], s['cutoff']):s for s in data['steps']}
    matches = {m['fixture']['id']:m for m in data['results']}
    bets = {(b['includeCupScores'], b['fixtureId']):b for b in data['bets']}
    observations = {False:[], True:[]}
    candidates_count = alternatives = 0
    for f in frames:
        step = steps[(f['includeCupScores'], f['predictedAt'])]
        learning = step['learning']['markets']
        assert instant(f['predictedAt']) <= instant(f['kickoff'])
        assert len({c['key'] for c in f['candidates']}) == len(f['candidates'])
        assert sum(c['primary'] for c in f['candidates']) == 1
        weights, means, scales = step['model']['weights'], step['model']['means'], step['model']['scales']
        x = list(map(float, f['features']))
        rates = [max(.05,min(6,math.exp(float(w[0])+sum(float(w[i+1])*(x[i]-float(means[i]))/float(scales[i]) for i in range(len(x)))))) for w in weights]
        eligible, allowed = [], []
        probabilities = {}
        for c in f['candidates']:
            q, p = c['quote'], float(c['probability'])
            assert instant(q['observedAt']) < instant(f['kickoff'])
            semantic = (market_family(q),str(q['market'].get('line')))
            if semantic not in probabilities: probabilities[semantic] = probability(q,rates)
            assert abs(probabilities[semantic]-p) < 1e-10
            assert c['key'] == '|'.join((market_family(q),q['bookmaker'],q['market']['id'],q['outcomeId']))
            assert p >= .5 and p*float(q['price']) >= 1.05 and q['price'] <= 5
            family = market_family(q)
            wrapper = dict(quote=q,features=f['features'],probability=p,kickoff=f['kickoff'],competitionId=f['competitionId'],season=f['season'])
            keys = decision_keys(wrapper,p)
            calibration = next((v for v in learning['families'] if v['family']==family), None)
            if calibration and calibration_supports(calibration,p):
                corrected = logistic(float(calibration['offset'])+float(calibration['slope'])*logit(p)) if calibration['accepted'] else p
                ev = corrected*float(q['price'])-1
                if corrected >= .5 and ev >= .05:
                    item = (c,corrected,ev)
                    eligible.append(item)
                    rule_list = next((v['rules'] for v in learning['conditions'] if v['family']==family),[])
                    if not keys.intersection(r['key'] for r in rule_list if r['excluded']): allowed.append(item)
            result = matches[f['fixtureId']]
            observations[f['includeCupScores']].append(dict(fixture=f['fixtureId'],predicted=instant(f['predictedAt']),settled=instant(result['fixture']['endedAt']),family=family,p=p,won=int(won(q,result['score'])),primary=c['primary'],candidate=c,frame=f,keys=keys))
            candidates_count += 1
            alternatives += not c['primary']
        def choice(items):
            return min(items,key=lambda t:(-t[2],t[0]['quote']['bookmaker'],t[0]['quote']['market']['id'],t[0]['quote']['outcomeId'])) if items else None
        expected = {}
        replacement_families = {market_family(c[0]['quote']) for c in allowed if not c[0]['primary']}
        for family in replacement_families:
            selected = choice([c for c in allowed if not c[0]['primary'] and market_family(c[0]['quote'])==family])
            expected['alternative:'+family] = selected[0]['key']
        if learning['version']=='conditional-markets-v2':
            permitted = {check['family'] for check in learning['replacements'] if check['allowed']}
            allowed = [c for c in allowed if c[0]['primary'] or market_family(c[0]['quote']) in permitted]
            eligible = [c for c in eligible if c[0]['primary'] or market_family(c[0]['quote']) in permitted]
        else:
            expected = {}  # Earlier frames did not retain per-family replacement shadows.
        for key, items in [('adaptive',allowed),('calibrated',eligible),('primary-only',[a for a in allowed if a[0]['primary']])]:
            selected = choice(items)
            if selected: expected[key] = selected[0]['key']
        assert expected == f['policyPicks']
        actual = bets.get((f['includeCupScores'],f['fixtureId']))
        selected = choice(allowed)
        assert bool(actual) == bool(selected)
        if actual:
            assert actual['quote'] == selected[0]['quote']
            assert abs(float(actual['probability'])-selected[1]) < 1e-10
    for step in data['steps']:
        snapshot = step['learning']['markets']; cut = instant(step['cutoff'])
        past = [o for o in observations[step['includeCupScores']] if o['settled'] < cut and o['predicted'] < o['settled'] and o['predicted'] < cut]
        assert snapshot['fixtures'] == len({o['fixture'] for o in past})
        assert snapshot['candidates'] == len(past)
        assert snapshot['alternatives'] == sum(not o['primary'] for o in past)
        assert (instant(snapshot['latestResult']) if snapshot['latestResult'] else None) == max((o['settled'] for o in past),default=None)
        cuts = sorted({o['predicted'] for o in past})
        boundary = cuts[min(len(cuts)-1,max(1,int(len(cuts)*.7)))] if len(cuts)>=2 else None
        for calibration in [snapshot['global']]+snapshot['families']:
            rows = past if calibration['family']=='all' else [o for o in past if o['family']==calibration['family']]
            train = [o for o in rows if boundary and o['predicted']<boundary and o['settled']<boundary]
            validation = [o for o in rows if boundary and o['predicted']>=boundary]
            assert calibration['support'] == len({o['fixture'] for o in rows})
            assert calibration['fitted'] == len({o['fixture'] for o in train})
            assert calibration['validation'] == len({o['fixture'] for o in validation})
            assert (instant(calibration['validationFrom']) if calibration['validationFrom'] else None) == boundary
            latest = max((o['settled'] for o in train),default=None)
            assert (instant(calibration['latestFitResult']) if calibration['latestFitResult'] else None) == latest
            if latest: assert latest < boundary
            if calibration['rawBrier'] is not None:
                prior = (0,1) if calibration['family']=='all' else (float(snapshot['global']['offset']),float(snapshot['global']['slope']))
                a,b = fit_market_coefficients(train,prior)
                assert abs(a-float(calibration['offset'])) < 1e-8 and abs(b-float(calibration['slope'])) < 1e-8
                raw_loss, corrected_loss = market_loss(validation,0,1),market_loss(validation,a,b)
                assert abs(raw_loss[0]-float(calibration['rawBrier']))<1e-10
                assert abs(corrected_loss[0]-float(calibration['calibratedBrier']))<1e-10
                assert calibration['accepted'] == (corrected_loss[0]+.001<raw_loss[0] and corrected_loss[1]<raw_loss[1])
                assert abs(min(o['p'] for o in train)-float(calibration['minProbability']))<1e-12
                assert abs(max(o['p'] for o in train)-float(calibration['maxProbability']))<1e-12
                a,b = (a,b) if calibration['accepted'] else (0,1)
                for band in calibration['bands']:
                    local = [o for o in validation if probability_band(o['p'])==band['band']]
                    selected = [o for o in local if logistic(a+b*logit(o['p']))>=.5 and logistic(a+b*logit(o['p']))*float(o['candidate']['quote']['price'])>=1.05]
                    by_fixture = {}
                    for o in selected: by_fixture.setdefault(o['fixture'],[]).append(o)
                    predictions = [sum(logistic(a+b*logit(o['p'])) for o in group)/len(group) for group in by_fixture.values()]
                    actuals = [sum(o['won'] for o in group)/len(group) for group in by_fixture.values()]
                    predicted = sum(predictions)/len(predictions) if predictions else 0
                    actual = sum(actuals)/len(actuals) if actuals else 0
                    assert band['support']==len({o['fixture'] for o in local}) and band['selected']==len(by_fixture)
                    assert abs(float(band['predicted'])-predicted)<1e-10 and abs(float(band['observed'])-actual)<1e-10
                    residuals = [p-y for p,y in zip(predictions,actuals)]
                    mean = sum(residuals)/len(residuals) if residuals else 0
                    se = math.sqrt(sum((v-mean)**2 for v in residuals)/(len(residuals)*(len(residuals)-1))) if len(residuals)>=2 else 1
                    assert band['ready']==(band['support']>=15 and band['selected']>=10 and predicted-actual<=.02+2*se)
        for family in snapshot['conditions']:
            representative = {}
            for o in past:
                if o['family']!=family['family']: continue
                previous = representative.get(o['fixture'])
                if previous is None or o['p']*float(o['candidate']['quote']['price']) > previous['p']*float(previous['candidate']['quote']['price']): representative[o['fixture']] = o
            for rule in family['rules']:
                own = []
                for o in representative.values():
                    if rule['key'] in o['keys']: own.append(o)
                assert len(own) == rule['support']
                if rule['excluded']:
                    assert rule['firstSupport']>=30 and rule['secondSupport']>=30 and rule['upperMeanReturn']<0
        unit = Decimal(str(json.loads(data['run']['config'])['unitProfit']))
        visible = [f for f in frames if f['includeCupScores']==step['includeCupScores'] and instant(matches[f['fixtureId']]['fixture']['endedAt'])<cut]
        if snapshot['version']=='conditional-markets-v2':
            audit_replacement_evidence(snapshot['replacements'],visible,matches,unit)
        for check in snapshot['policies']:
            count = 0; turnover = net = Decimal(0)
            for f in visible:
                picked = next((c for c in f['candidates'] if c['key']==f['policyPicks'].get(check['policy'])),None)
                if not picked: continue
                q = picked['quote']; stake = max(Decimal('.01'),(unit/(q['price']-1)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP))
                turnover += stake
                net += (stake*(q['price']-1)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP) if won(q,matches[f['fixtureId']]['score']) else -stake
                count += 1
            assert check['settled']==count and abs(float(turnover)-float(check['turnover']))<1e-7 and abs(float(net)-float(check['net']))<1e-7
    return dict(frames=len(frames),candidates=candidates_count,alternatives=alternatives,status='PASS')


def audit_replacement_evidence(checks, frames, matches, unit):
    groups = {}
    def money(candidate, score):
        q=candidate['quote'];stake=max(Decimal('.01'),(unit/(q['price']-1)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP))
        gain=(stake*(q['price']-1)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP) if won(q,score) else -stake
        return float(stake),float(gain)
    for f in frames:
        if not instant(f['predictedAt'])<instant(matches[f['fixtureId']]['fixture']['endedAt']):continue
        score=matches[f['fixtureId']]['score'];primary=next(c for c in f['candidates'] if c['primary'])
        primary_net=money(primary,score)[1]
        for c in f['candidates']:
            family=market_family(c['quote'])
            if f['policyPicks'].get('alternative:'+family)!=c['key']:continue
            stake,net=money(c,score)
            groups.setdefault(family,[]).append(dict(fixture=f['fixtureId'],week=f['predictedAt'],stake=stake,net=net,difference=net-primary_net))
    assert set(groups)=={c['family'] for c in checks}
    for check in checks:
        rows=groups[check['family']];weeks=sorted({r['week'] for r in rows});middle=weeks[len(weeks)//2]
        first=[r for r in rows if r['week']<middle];second=[r for r in rows if r['week']>=middle]
        def roi(items):return sum(r['net'] for r in items)/max(.01,sum(r['stake'] for r in items))
        assert check['support']==len(rows)==len({r['fixture'] for r in rows})
        assert check['weeks']==len(weeks) and check['firstSupport']==len(first) and check['secondSupport']==len(second)
        assert abs(float(check['firstRoi'])-roi(first))<1e-9 and abs(float(check['secondRoi'])-roi(second))<1e-9
        if len(rows)<30 or len(weeks)<6 or len(first)<10 or len(second)<10:
            assert not check['allowed'] and check['lowerRoi'] is None and check['lowerDifference'] is None
            continue
        rate=roi(rows);difference=sum(r['difference'] for r in rows)/len(rows)
        clustered=[[r for r in rows if r['week']==week] for week in weeks]
        adjustment=len(weeks)/(len(weeks)-1)
        roi_se=math.sqrt(adjustment*sum(sum(r['net']-rate*r['stake'] for r in group)**2 for group in clustered))/sum(r['stake'] for r in rows)
        paired_se=math.sqrt(adjustment*sum(sum(r['difference']-difference for r in group)**2 for group in clustered))/len(rows)
        lower,paired=rate-2*roi_se,difference-2*paired_se
        assert abs(float(check['lowerRoi'])-lower)<1e-9 and abs(float(check['lowerDifference'])-paired)<1e-9
        assert check['allowed']==(roi(first)>0 and roi(second)>0 and lower>0 and paired>0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', required=True)
    parser.add_argument('--capture', type=Path)
    parser.add_argument('--verify', type=Path)
    parser.add_argument('--run', action='append', default=[])
    parser.add_argument('--api', default='http://127.0.0.1:8080')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    for identifier in [args.reference] + args.run:
        assert re.fullmatch(r'[A-Za-z0-9:_-]{1,80}', identifier)
    address = urllib.parse.urlsplit(args.api)
    assert address.scheme == 'http' and address.hostname in ('127.0.0.1', 'localhost')
    state = snapshot(args.reference)
    assert state['original'] is not None
    if args.capture:
        assert not args.capture.exists(), 'Preservation manifest already exists'
        args.capture.parent.mkdir(parents=True, exist_ok=True)
        args.capture.write_text(json.dumps(state, indent=2, default=str), encoding='utf-8')
    if args.verify:
        saved = json.loads(args.verify.read_text(encoding='utf-8'))
        assert saved == {key:state[key] for key in saved}, 'Original campaign or source counters changed'
    checked = [audit_run(run, args.api) for run in args.run]
    report = dict(status='PASS', preservation=bool(args.verify), campaigns=checked, sources=state['sources'])
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
