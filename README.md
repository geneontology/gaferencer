[![Build Status](https://travis-ci.org/geneontology/gaferencer.svg?branch=master)](https://travis-ci.org/geneontology/gaferencer)

# gaferencer

```
Usage: gaferencer [gaf|taxa] [options] <args>...

  --catalog <value>        Catalog file for ontology loading (optional)
  --contexts <first.jsonld>,<second.jsonld>...
                           Paths to JSON-LD context files
  --ontfile <value>        Ontology IRI is local filename (default false)
Command: gaf <ontology> <gaf> <annotation-inferences-outfile>
compute GAF inferences
  <ontology>               Ontology IRI
  <gaf>                    Path to GAF
  <annotation-inferences-outfile>
                           File to output annotation inferences (JSON)
Command: taxa <ontology> <taxon-list> <taxon-table>
compute taxon-by-GO table
  <ontology>               Ontology IRI
  <taxon-list>             Path to taxon list
  <taxon-table>            Path to taxon table output
```
