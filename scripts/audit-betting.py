"""Verifica indipendente di schedine, quote, probabilita e bilanci esportati. Nessuna scrittura DB/API provider."""
import datetime as dt
import json
import math
import pathlib
import urllib.request
import runpy
from decimal import Decimal, ROUND_HALF_UP

ROOT = pathlib.Path(__file__).resolve().parents[1]
nff_scores = runpy.run_path(str(ROOT / 'scripts/audit-data.py'))['nff_scores']
def load(name):
    return json.loads((ROOT / '.local/audit' / name).read_text(encoding='utf-8-sig'), parse_float=Decimal)
def time(s): return dt.datetime.fromisoformat(s.replace('Z', '+00:00'))
def score_period(score, period):
    if period == 'fulltime': return score['homeFt'], score['awayFt']
    if period == 'p1': return score['homeHt'], score['awayHt']
    assert period == 'p2'
    return score['homeFt'] - score['homeHt'], score['awayFt'] - score['awayHt']
def result(h, a): return '1' if h > a else 'X' if h == a else '2'
def won(q, score):
    h, a = score_period(score, q['market']['period'])
    kind, outcome = q['market']['type'], q['outcome']
    if kind == '1x2': return outcome == result(h, a)
    if kind == 'bothteamsscore': return (h > 0 and a > 0) == (outcome == 'Yes')
    if kind == 'correctscore': return outcome.replace(':', '-') == f'{h}-{a}'
    if kind == 'halftime-fulltime': return outcome == result(score['homeHt'], score['awayHt']) + '/' + result(score['homeFt'], score['awayFt'])
    assert kind in ('totals', 'teamtotals-team1', 'teamtotals-team2')
    line = q['market']['line']; assert line % 1 == Decimal('.5')
    total = h + a if kind == 'totals' else h if kind.endswith('team1') else a
    return total > line if outcome == 'Over' else total < line

def lambdas(snapshot, fixture, cups):
    data, model = snapshot['dataset'], snapshot['model']['cupsModel' if cups else 'leagueModel']
    cutoff, kickoff = time(data['cutoff']), time(fixture['kickoff'])
    def recent(team):
        all_games = [m for m in data['trainingHistory'] + data['leagueHistory'] if team in (m['fixture']['homeId'], m['fixture']['awayId']) and time(m['fixture']['endedAt']) <= cutoff]
        return sorted(all_games, key=lambda m: time(m['fixture']['kickoff']), reverse=True)[:5]
    def rate(team, scored, first):
        games = recent(team); total = 1.2 if first else 1.4
        for m in games:
            side = 'home' if (m['fixture']['homeId'] == team) == scored else 'away'
            total += m['score'][side+'Ht'] if first else m['score'][side+'Ft'] - m['score'][side+'Ht']
        return total / (len(games) + 2)
    h, a = fixture['homeId'], fixture['awayId']
    features = [rate(h,True,True),rate(a,False,True),rate(a,True,True),rate(h,False,True),rate(h,True,False),rate(a,False,False),rate(a,True,False),rate(h,False,False)]
    def calendar(team): return [f for f in data['calendar'] if team in (f['homeId'], f['awayId']) and time(f['endedAt']) <= cutoff and f['tournamentId'] in ({20,22,7,29,679,34480} if cups else {20})]
    home, away = calendar(h), calendar(a)
    def rest(events): return min(14, (kickoff-max(time(f['kickoff']) for f in events)).total_seconds()/86400) if events else 14
    def count(events, days, cup=False, team=None): return sum(time(f['kickoff']) > kickoff-dt.timedelta(days=days) and (not cup or f['tournamentId'] in {7,29,679,34480}) and (team is None or f['awayId'] == team) for f in events)
    features += [rest(home),rest(away),count(home,14),count(away,14),count(home,14,True) if cups else 0,count(away,14,True) if cups else 0,count(home,7,team=h),count(away,7,team=a)]
    return [max(.05,min(6,math.exp(float(w[0])+sum(float(w[i+1])*(features[i]-float(model['means'][i]))/float(model['scales'][i]) for i in range(16))))) for w in model['weights']]

def probability(q, rates):
    def pmf(mean, goals): return math.exp(-mean) * mean ** goals / math.factorial(goals)
    period = q['market']['period']; kind = q['market']['type']; outcome = q['outcome']
    h,a = rates[:2] if period == 'p1' else rates[2:] if period == 'p2' else (rates[0]+rates[2], rates[1]+rates[3])
    if kind == 'bothteamsscore':
        p=(1-math.exp(-h))*(1-math.exp(-a)); return p if outcome == 'Yes' else 1-p
    if kind == '1x2': return sum(pmf(h,x)*pmf(a,y) for x in range(65) for y in range(65) if result(x,y)==outcome)
    if kind == 'correctscore':
        x,y=map(int,outcome.replace(':','-').split('-')); return pmf(h,x)*pmf(a,y)
    if kind == 'halftime-fulltime':
        ht,ft=outcome.split('/'); total=0.
        for h1 in range(33):
            for a1 in range(33):
                if result(h1,a1)!=ht: continue
                for h2 in range(33):
                    for a2 in range(33):
                        if result(h1+h2,a1+a2)==ft: total+=pmf(rates[0],h1)*pmf(rates[1],a1)*pmf(rates[2],h2)*pmf(rates[3],a2)
        return total
    assert kind in ('totals','teamtotals-team1','teamtotals-team2')
    mean=h+a if kind=='totals' else h if kind.endswith('team1') else a
    p=sum(pmf(mean,n) for n in range(math.floor(q['market']['line'])+1))
    return 1-p if outcome=='Over' else p

snapshots={s['id']:s for s in load('snapshots.json')}
rounds=load('betting-rounds.json'); assert rounds
latest=max(snapshots.values(),key=lambda s:time(s['dataset']['cutoff']))
scores={m['fixture']['id']:m['score'] for m in latest['dataset']['leagueHistory']}
with urllib.request.urlopen('http://127.0.0.1:8080/api/betting',timeout=20) as r: public=json.loads(r.read(),parse_float=Decimal)
checked=0
def available_at(previous, source, variant, initial):
    known={m['fixture']['id']:m['score'] for m in source['dataset']['leagueHistory']
           if time(m['fixture']['endedAt']) <= time(source['dataset']['cutoff'])}
    capital=initial
    for old in previous:
        for slip in old[variant]:
            capital-=slip['stake']
            if all(l['quote']['fixtureId'] in known for l in slip['legs']) and all(won(l['quote'],known[l['quote']['fixtureId']]) for l in slip['legs']):
                capital+=(slip['stake']*slip['price']).quantize(Decimal('.01'),rounding=ROUND_HALF_UP)
    return capital

for variant in ('cups','league'):
    initial=Decimal(rounds[0]['config']['unitValue'])*Decimal(rounds[0]['config']['initialBankrollUnits'])
    net=Decimal(0); closed=Decimal(0); pending=Decimal(0); brier=0.; resolved=0
    peak=initial; drawdown=Decimal(0); singles=Decimal(0); doubles=Decimal(0)
    for index, round_ in enumerate(rounds):
        source=snapshots[round_['scenarioId']]; assert round_['datasetSha256']==source['datasetSha256']
        assert round_['policyVersion']=='net-profit-unit-v3'
        assert 'maxRoundUnits' not in round_['config']
        opening=round_['openingBankroll' if variant=='cups' else 'leagueOpeningBankroll']
        assert opening==available_at(rounds[:index], source, variant, initial)
        quotes={(q['fixtureId'],q['bookmaker'],q['market']['id'],q['outcomeId']):q for q in source['dataset']['quotes']}
        fixtures={f['id']:f for f in source['dataset']['targets']}
        used=set(); spent=Decimal(0)
        for slip in round_[variant]:
            price=Decimal(1); p=1.; complete=True; success=True
            for leg in slip['legs']:
                q=leg['quote']; assert q==quotes[(q['fixtureId'],q['bookmaker'],q['market']['id'],q['outcomeId'])]
                assert q['fixtureId'] not in used; used.add(q['fixtureId'])
                assert q['bookmaker']==slip['bookmaker']=='unibet'
                assert time(q['observedAt']) <= time(round_['cutoff']) < time(leg['kickoff'])
                predicted=probability(q,lambdas(source,fixtures[q['fixtureId']],variant=='cups'))
                assert abs(predicted-float(leg['probability']))<1e-10
                price*=q['price']; p*=predicted; checked+=1
                if q['fixtureId'] not in scores: complete=False
                else:
                    outcome=won(q,scores[q['fixtureId']]); success &= outcome
                    brier+=(predicted-int(outcome))**2; resolved+=1
            assert price==slip['price'] and abs(p-float(slip['probability']))<1e-10
            assert abs(p*float(price)-1-float(slip['expectedReturn']))<1e-10
            assert slip['units']==1 and price > 1
            assert slip['stake']==max(Decimal('.01'), (Decimal(round_['config']['unitValue'])/(price-1)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP))
            gross=(slip['stake']*price).quantize(Decimal('.01'),rounding=ROUND_HALF_UP)
            assert slip['potentialPayout']==gross and slip['potentialProfit']==gross-slip['stake']
            spent+=slip['stake']
            if complete:
                gain=gross-slip['stake'] if success else -slip['stake']
                closed+=slip['stake']; net+=gain
                if slip['kind']=='SINGLE': singles+=gain
                else: doubles+=gain
            else: pending+=slip['stake']
        assert spent <= opening
        peak=max(peak,initial+net); drawdown=max(drawdown,peak-(initial+net))
    actual=public[variant]
    assert (actual['profit'],actual['closedStake'],actual['pendingStake'],actual['bankroll'],actual['available'])==(net,closed,pending,initial+net,initial+net-pending)
    assert actual['resolvedLegs']==resolved
    assert (actual['maxDrawdown'],actual['singlesProfit'],actual['doublesProfit'])==(drawdown,singles,doubles)
    if closed: assert abs(float(actual['roi'])-float(net/closed))<1e-9
    if resolved: assert abs(float(actual['binaryBrier'])-brier/resolved)<1e-10
reveals=load('betting-reveals.json')
for reveal in reveals:
    source=snapshots[reveal['scenarioId']]
    round_=next(r for r in rounds if r['scenarioId']==reveal['scenarioId'])
    slip=next(s for s in round_[reveal['variant']] if s['id']==reveal['slipId'])
    assert reveal['stake']==slip['stake']
    assert [row['leg'] for row in reveal['results']]==slip['legs']
    official=nff_scores(ROOT,[{'fixture':f} for f in source['dataset']['targets']])
    complete=True; success=True
    for row in reveal['results']:
        score=row['score']
        if score is None: complete=False; continue
        ht,ft=official[row['leg']['quote']['fixtureId']]
        assert [score['homeHt'],score['awayHt']]==ht and [score['homeFt'],score['awayFt']]==ft
        assert row['won']==won(row['leg']['quote'],score)
        success &= row['won']
    assert reveal['status']==('PENDING' if not complete else 'WON' if success else 'LOST')
    if complete:
        payout=(slip['stake']*slip['price']).quantize(Decimal('.01'),rounding=ROUND_HALF_UP) if success else Decimal(0)
        assert reveal['payout']==payout and reveal['profit']==payout-slip['stake']
    else: assert reveal['payout'] is None and reveal['profit'] is None

revisions=load('betting-round-revisions.json')
for original in revisions:
    current=next(r for r in rounds if r['scenarioId']==original['scenarioId'])
    for field in ('scenarioId','datasetSha256','modelId','cutoff','frozenAt'):
        assert original[field]==current[field]
    for field in ('unitValue','initialBankrollUnits'):
        assert original['config'][field]==current['config'][field]
    for variant in ('cups','league'):
        assert len(original[variant])==len(current[variant])
        for before,after in zip(original[variant],current[variant]):
            for field in ('id','kind','bookmaker','legs','price','probability','expectedReturn'):
                assert before[field]==after[field], (original['scenarioId'],variant,field)
reveal_revisions=load('betting-reveal-revisions.json')
for original in reveal_revisions:
    current=next(r for r in reveals if all(r[k]==original[k] for k in ('scenarioId','variant','slipId')))
    # Gli esiti finali sono immutabili; un'attesa può essere risolta dopo il ricalcolo.
    if original['status']!='PENDING':
        for field in ('checkedAt','status','results'):
            assert original[field]==current[field]
report={'status':'PASS','rounds':len(rounds),'legs_checked_in_both_models':checked,'probabilities_recomputed':True,'stakes_payouts_bankroll_verified':True,'saved_reveals_verified':len(reveals),'original_rounds_preserved':len(revisions),'original_reveals_preserved':len(reveal_revisions)}
(ROOT/'.local/audit/betting-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
