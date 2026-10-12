#!/bin/sh
# Downloads the ChhoeTaigi datasets compiled into the app lexicon by
#   ./gradlew :tools:dictbuilder:buildLexicon
# Only permissively licensed datasets are fetched (see data/sources/README.md).
set -eu
cd "$(dirname "$0")/.."
mkdir -p data/sources
base=https://raw.githubusercontent.com/ChhoeTaigi/ChhoeTaigiDatabase/master/ChhoeTaigiDatabase
for name in ChhoeTaigi_TaihoaSoanntengTuichiautian ChhoeTaigi_iTaigiHoataiTuichiautian; do
    echo "fetching $name.csv"
    curl -fsSL -o "data/sources/$name.csv" "$base/$name.csv"
done
