#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# source the get_project_xml function
source "$SCRIPT_DIR/../../../deployments/utils.sh"

INPUT_JSON="$SCRIPT_DIR/../../inputs/test_get_project_xml.json"
OUTPUT_XML="$SCRIPT_DIR/../../outputs/result_test_get_project_xml.xml"
EXPECTED_XML="$SCRIPT_DIR/../../outputs/test_get_project_xml.xml"
TEMPLATE_PATH="$SCRIPT_DIR/../../../deployments/templates/xml/project_template.xml"

# Call the function
generate_project_xml "$INPUT_JSON" "$OUTPUT_XML" "$TEMPLATE_PATH"

# Compare the result
if diff -q "$OUTPUT_XML" "$EXPECTED_XML" > /dev/null; then
    echo "Test passed: Generated XML matches expected output."
    rm "$OUTPUT_XML"
    exit 0
else
    echo "Test failed: Generated XML does not match expected output."
    echo "Diff:"
    diff "$OUTPUT_XML" "$EXPECTED_XML"
    rm "$OUTPUT_XML"
    exit 1
fi

