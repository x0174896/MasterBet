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
          ORDER BY step_number,include_cup_scores)) FROM lab_step WHERE run_id='{reference}'));
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
      'shadows',(SELECT coalesce(json_agg(payload::json),'[]') FROM lab_learning_prediction WHERE run_id='{run_id}'));
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
            if learning['calibration']['accepted'] and b['modelVersion'] != 'shadow-v1':
                p = max(.001, min(.999, p))
                p = 1 / (1 + math.exp(-(math.log(p / (1-p)) + float(learning['calibration']['offset']))))
            if b['modelVersion'] != 'shadow-v1':
                assert not decision_keys(b, raw).intersection(r['key'] for r in learning['rules'] if r['excluded'])
        assert abs(p - float(b['probability'])) < 1e-10
        assert abs(float(b['expectedValue']) - (p * float(b['quote']['price']) - 1)) < 1e-10
        assert b['probability'] >= Decimal('.5') and b['expectedValue'] >= Decimal('.05')
        assert b['quote']['price'] <= 5
        assert b['stake'] == max(Decimal('.01'), (b['unitProfit'] / (b['quote']['price'] - 1)).quantize(Decimal('.01'), rounding=ROUND_HALF_UP))
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
    return dict(run=run_id, steps=len(steps), shadows=len(data['shadows']), modes=checked)


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
        assert json.loads(args.verify.read_text(encoding='utf-8')) == state, 'Original campaign or source counters changed'
    checked = [audit_run(run, args.api) for run in args.run]
    report = dict(status='PASS', preservation=bool(args.verify), campaigns=checked, sources=state['sources'])
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
