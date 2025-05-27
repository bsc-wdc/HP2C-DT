#!/bin/bash

generate_project_xml() {
    local json_path="$1"
    local output_path="$2"
    local template_path="$3"

    local cpu arch memory storage
    cpu=$(jq -r '.compss.project.cpu' "$json_path")
    arch=$(jq -r '.compss.project.arch' "$json_path")
    memory=$(jq -r '.compss.project.memory' "$json_path")
    storage=$(jq -r '.compss.project.storage' "$json_path")

    [[ "$cpu" != "null" ]] && CPU_LINE="<ComputingUnits>$cpu</ComputingUnits>" || CPU_LINE="<ComputingUnits>1</ComputingUnits>"
    [[ "$arch" != "null" ]] && ARCH_LINE="<Architecture>$arch</Architecture>" || ARCH_LINE=""
    [[ "$memory" != "null" ]] && MEMORY_LINE="<Memory><Size>$memory</Size></Memory>" || MEMORY_LINE=""
    [[ "$storage" != "null" ]] && STORAGE_LINE="<Storage><Size>$storage</Size></Storage>" || STORAGE_LINE=""

    sed -e "s|{{CPU_LINE}}|$CPU_LINE|" \
        -e "s|{{ARCH_LINE}}|$ARCH_LINE|" \
        -e "s|{{MEMORY_LINE}}|$MEMORY_LINE|" \
        -e "s|{{STORAGE_LINE}}|$STORAGE_LINE|" \
        "$template_path" > "$output_path"
}
