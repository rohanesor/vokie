# Emergency-vocabulary gap report

## Result: UNKNOWN — corpus text was not available

The official IndicVoices-R repository describes normalized transcript fields, but no target-language manifests or transcripts were obtainable in the metadata-only phase. LibriTTS archive transcripts were not downloaded. Coverage cannot be inferred from a corpus title, published hours, or language membership.

The following must be counted per language before training:

| Category | Required examples / forms | Current coverage |
|---|---|---|
| Emergency alerts | send help, danger, injured, emergency, warning | UNKNOWN |
| Instructions | move, do not enter, call, wait, evacuate | UNKNOWN |
| Locations | hospital, shelter, station, building, entrance, street | UNKNOWN |
| Numbers | cardinal/ordinal numbers, counts, phone-like sequences | UNKNOWN |
| Dates/times | dates, clock times, durations | UNKNOWN |
| Names | representative Indian personal and place names | UNKNOWN |
| Mixed text | English abbreviations/loanwords and code-switching | UNKNOWN |
| Punctuation | pauses, questions, alerts, delimiters | UNKNOWN |

## Separate evaluation set

Create a native-speaker/linguist-reviewed, license-cleared evaluation set outside training data. It must contain short emergency, long emergency, location, numeric, name/location, and instruction sentences for every target language. Keep stable IDs, translator/reviewer provenance, canonical Unicode text, romanization only where needed for review, and no overlap with training text. Do not add these sentences to training based solely on poor coverage; any augmentation decision needs separate legal and evaluation review.

## Frontend implications

The local frontend must explicitly normalize numbers, dates, abbreviations, punctuation, Unicode NFC, and approved mixed-language tokens per routed packet language. Corpus audit results determine rules; Phase 2F creates no unvalidated normalization rewrite.
