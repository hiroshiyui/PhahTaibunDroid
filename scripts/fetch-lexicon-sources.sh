#!/bin/sh
# Downloads the datasets compiled into the app lexicon by
#   ./gradlew :tools:dictbuilder:buildLexicon
# Only permissively licensed datasets are fetched (see NOTICE).
set -eu
cd "$(dirname "$0")/.."
mkdir -p data/sources
base=https://raw.githubusercontent.com/ChhoeTaigi/ChhoeTaigiDatabase/master/ChhoeTaigiDatabase
for name in ChhoeTaigi_TaihoaSoanntengTuichiautian ChhoeTaigi_iTaigiHoataiTuichiautian; do
    echo "fetching $name.csv"
    curl -fsSL -o "data/sources/$name.csv" "$base/$name.csv"
done


# iCorpus 臺華平行新聞語料庫 is deliberately not fetched: the 漢字臺羅版 edition is labelled
# CC BY 4.0, but the corpus it is built from is CC BY-NC-SA 4.0 (中央研究院資訊所), which is
# not compatible with this project. tools/dictbuilder still uses a corpus in data/sources/icorpus/
# (hanji.txt + tailo.txt, line-aligned) if one is present, for local experiments only: never
# commit assets built with it.
