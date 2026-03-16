ASSETS DIRECTORY
================

This directory requires a file: countries_simple.geojson

Download it with:

  curl -L "https://raw.githubusercontent.com/datasets/geo-countries/master/data/countries.geojson" \
    -o countries_simple.geojson

Requirements for the GeoJSON:
- Must be a FeatureCollection
- Each feature must have an "ISO_A2" property with the 2-letter ISO country code
  (the Natural Earth dataset uses ISO_A2)
- Geometry can be any valid GeoJSON geometry (Polygon or MultiPolygon)

The app injects a "score" property (0.0-1.0) from the Pi backend API into each
feature at runtime and applies a color scale via MapLibre fill layer.

If the file is missing, the map will show without heatmap coloring (no crash).
