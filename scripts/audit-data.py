"""Audit indipendente: archivio originale -> snapshot -> API. Nessuna chiamata OddsPapi.
Eseguire dalla radice dopo l'esportazione locale dello snapshot (vedere setup.md).
Usa soltanto la libreria standard Python; non modifica database o sorgenti.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import pathlib
import urllib.request
from decimal import Decimal


def instant(value):
    return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), parse_float=Decimal)


def official_crosscheck(root, history):
    path = root / '.local/audit/nff-results.json'
    if not path.exists():
        return 'NOT_RUN: referti ufficiali locali assenti'
    aliases = {'HamKam':'HamKam','Viking FK':'Viking','Molde FK':'Molde','Rosenborg BK':'Rosenborg',
               'Kristiansund BK':'Kristiansund','SK Brann':'Brann','KFUM Oslo':'KFUM','IK Start':'Start',
               'Vaalerenga IF':'Vålerenga','Sandefjord Fotball':'Sandefjord Fotball','Aalesunds FK':'Aalesund',
               'Lillestroem SK':'Lillestrøm','Tromsoe IL':'Tromsø','Fredrikstad FK':'Fredrikstad',
               'Sarpsborg 08':'Sarpsborg 08','Bodoe/Glimt':'Bodø/Glimt'}
    records = {(r['date'], r['home'], r['away']): r for r in load(path)}
    assert len(records) == len(history) == 47
    for match in history:
        f, s = match['fixture'], match['score']
        date = instant(f['kickoff'])
        ref = records[(date.strftime('%d.%m.%Y'), aliases[f['home']], aliases[f['away']])]
        assert [s['homeFt'], s['awayFt']] == ref['ft'], ('Finale NFF', f['id'], ref['url'])
        assert [s['homeHt'], s['awayHt']] == ref['ht'], ('Primo tempo NFF', f['id'], ref['url'])
        # Audit limitato a marzo/aprile 2026: Oslo CET fino al 28 marzo, poi CEST.
        assert date.year == 2026 and date.month in (3, 4)
        offset = 1 if date.month == 3 and date.day < 29 else 2
        assert (date + dt.timedelta(hours=offset)).strftime('%H:%M') == ref['time'], ('Orario NFF', f['id'])
    return 'PASS: 47 primi tempi, finali e orari contro referti NFF'


def audit(root, snapshot_path, api):
    snapshot = load(snapshot_path)
    data = snapshot['dataset']
    cutoff = instant(data['cutoff'])
    archives = []
    for path in (root / '.local/data/raw').glob('*.json'):
        envelope = load(path)
        assert hashlib.sha256(envelope['body'].encode()).hexdigest() == envelope['sha256'], path.name
        assert 'apiKey' not in envelope['request'], 'Segreto nella chiave cache'
        archives.append((path.name, json.loads(envelope['body'], parse_float=Decimal)))
    scores = {body['fixtureId']: body['scores']['periods'] for name, body in archives if name.startswith('scores-')}
    catalog = next(body for name, body in archives if name.startswith('markets-'))
    markets = {str(m['marketId']): m for m in catalog}
    histories = data['trainingHistory'] + data['leagueHistory'] + data['cupResults']
    assert len(histories) == 129
    for match in histories:
        fixture, score = match['fixture'], match['score']
        assert instant(fixture['endedAt']) <= cutoff
        raw = scores[fixture['id']]
        final = raw.get('fulltime')
        if final is None:
            assert fixture['tournamentId'] == 20 and fixture['seasonId'] in (127337, 138092)
            final = raw['result']
        for side, participant in [('home', 'participant1Score'), ('away', 'participant2Score')]:
            assert score[side + 'Ht'] == raw['p1'][participant]
            assert score[side + 'Ft'] == final[participant]
            assert score[side + 'Ft'] >= score[side + 'Ht'] >= 0
    assert all(instant(m['fixture']['endedAt']) < dt.datetime(2026, 1, 1, tzinfo=dt.timezone.utc) for m in data['trainingHistory'])
    assert all(m['fixture']['seasonId'] == 127337 for m in data['trainingHistory'])
    target_ids = {f['id'] for f in data['targets']}
    assert len(target_ids) == 8
    assert not target_ids.intersection(scores), 'Risultati target scaricati'
    assert all(f['endedAt'] is None and instant(f['kickoff']) > cutoff for f in data['targets'])
    expected = {}
    unavailable = collections.Counter()
    for name, body in archives:
        if not name.startswith('historical-odds-') or body['fixtureId'] not in target_ids:
            continue
        for book, bookmaker in body['bookmakers'].items():
            for market_id, market in bookmaker['markets'].items():
                if markets[market_id]['playerProp']:
                    continue
                for outcome_id, outcome in market['outcomes'].items():
                    updates = [u for u in outcome.get('players', {}).get('0', []) if instant(u['createdAt']) <= cutoff]
                    if not updates:
                        unavailable['no_pre_cutoff_price'] += 1
                        continue
                    latest = max(updates, key=lambda u: instant(u['createdAt']))
                    same = [u for u in updates if instant(u['createdAt']) == instant(latest['createdAt'])]
                    assert len({(u['price'], u['active']) for u in same}) == 1
                    if not latest['active'] or latest['price'] <= 1:
                        unavailable['inactive_or_invalid_latest'] += 1
                        continue
                    expected[(body['fixtureId'], book, market_id, outcome_id)] = latest
    actual = {(q['fixtureId'], q['bookmaker'], q['market']['id'], q['outcomeId']): q for q in data['quotes']}
    assert len(actual) == len(data['quotes'])
    assert actual.keys() == expected.keys(), 'Quote perse, aggiunte o riattivate'
    for key, quote in actual.items():
        raw = expected[key]
        assert quote['price'] == raw['price'] and instant(quote['observedAt']) == instant(raw['createdAt'])
        market = markets[key[2]]
        assert quote['market']['type'] == market['marketType'] and quote['market']['period'] == market['period']
        assert quote['market']['line'] == market['handicap']
        labels = {str(o['outcomeId']): o['outcomeName'] for o in market['outcomes']}
        assert quote['outcome'] == labels[key[3]]
    assert len(snapshot['predictions']) == len(actual)
    for prediction in snapshot['predictions']:
        probability = prediction['probability']
        if probability is None:
            assert prediction['expectedReturn'] is None and not prediction['suggested']
        else:
            assert 0 <= probability <= 1
            ev = probability * prediction['quote']['price'] - 1
            assert abs(ev - prediction['expectedReturn']) < Decimal('1e-12')
            assert prediction['suggested'] == (ev >= Decimal('.05'))
    with urllib.request.urlopen(api + '/api/scenario', timeout=15) as response:
        public = json.loads(response.read(), parse_float=Decimal)
    assert public['datasetSha256'] == snapshot['datasetSha256']
    assert public['historicalMatches'] == 47 and public['quoteCount'] == len(actual)
    table = {t['teamId']: t for t in public['standings']}
    assert sum(t['played'] for t in table.values()) == 94
    assert sum(t['goalsFor'] for t in table.values()) == sum(t['goalsAgainst'] for t in table.values())
    for team, stats in table.items():
        games = [m for m in data['leagueHistory'] if team in (m['fixture']['homeId'], m['fixture']['awayId'])]
        gf = ga = hf = ha = points = home_points = away_points = 0
        for match in games:
            home = match['fixture']['homeId'] == team
            own, other = ('home', 'away') if home else ('away', 'home')
            s = match['score']
            gf += s[own+'Ft']; ga += s[other+'Ft']; hf += s[own+'Ht']; ha += s[other+'Ht']
            p = 3 if s[own+'Ft'] > s[other+'Ft'] else 1 if s[own+'Ft'] == s[other+'Ft'] else 0
            points += p
            if home: home_points += p
            else: away_points += p
        assert (stats['played'], stats['points'], stats['goalsFor'], stats['goalsAgainst'], stats['firstHalfFor'], stats['firstHalfAgainst'], stats['secondHalfFor'], stats['secondHalfAgainst'], stats['homePoints'], stats['awayPoints']) == (len(games), points, gf, ga, hf, ha, gf-hf, ga-ha, home_points, away_points)
    for fixture_id in target_ids:
        with urllib.request.urlopen(api + '/api/matches/' + fixture_id + '/quotes', timeout=15) as response:
            quotes = json.loads(response.read(), parse_float=Decimal)
        assert len(quotes) == sum(k[0] == fixture_id for k in actual)
        for q in quotes:
            original = actual[(fixture_id, q['bookmaker'], q['marketId'], q['outcomeId'])]
            assert q['price'] == original['price'] and q['line'] == original['market']['line']
    official = official_crosscheck(root, data['leagueHistory'])
    report = dict(status='PASS', raw_responses=len(archives), scores_checked=len(histories), quotes_checked=len(actual), official_crosscheck=official,
                  teams_checked=len(table), target_results_absent=True, unavailable=dict(unavailable), dataset_sha256=snapshot['datasetSha256'])
    print(json.dumps(report, indent=2))
    (root / '.local/audit/report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', default='.local/audit/snapshot.json')
    parser.add_argument('--api', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    audit(pathlib.Path.cwd(), pathlib.Path(args.snapshot), args.api)
