"""Finds each candidate's wording in the local hadith-json copy (by book) and reports matches."""
import json, sys, collections
sys.path.insert(0, '.')
from textnorm import norm, norm_with_map
from candidates import C, CATEGORIES, PRACTICES

DB = '/home/claude/ref/hadith-json/db/by_book/the_9_books/'
books = {}
def book(name):
    if name not in books:
        d = json.load(open(DB + name + '.json'))
        books[name] = [(h['idInBook'], h['arabic'], norm(h['arabic'])) for h in d['hadiths']]
    return books[name]

print(len(C), 'candidates;', collections.Counter(c['cat'] for c in C))
bad = 0
for c in C:
    bk = c['ref'].split(':')[0]
    phrases = [c['find']] if c['find'] else [c['find_from'], c['find_to']]
    if c['find_evening']: phrases.append(c['find_evening'])
    phrases = [norm(p) for p in phrases]
    hits = [i for i, a, n in book(bk) if all(p in n for p in phrases)]
    if not hits:
        bad += 1
        # partial diagnosis: which phrase fails
        miss = [p for p in phrases if not any(p in n for _, _, n in book(bk))]
        print('NO MATCH', c['id'], c['ref'], 'missing:', miss[:2])
    else:
        print('ok', c['id'], c['ref'], 'dataset ids', hits[:5])
print('unmatched', bad)
