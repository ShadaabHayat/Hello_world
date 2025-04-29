{{- define "recursiveLoad" }}
  # Define params in/for scope
  {{ $files := .files }}
  {{ $filePath := .filePath }}
  {{ $processed := .processed | default (list) }}
  {{ $content := .content | default (dict) }}
  # Read file content
  {{ $current := fromYaml ($files.Get $filePath) }}
  # Append file name to processed list (prevent cyclic inheritance)
  {{ $processed = append $processed $filePath }}
  # Check for inheritance parameter and remove from data read
  {{ $inheritPath := coalesce $content.inherit $current.inherit "" }}
  {{ if $content.inherit }}
    {{ $_ := unset $content "inherit" }}
  {{ end }}
  {{ if $current.inherit }}
    {{ $_ := unset $current "inherit" }}
  {{ end }}
  # Merge with existing content
  {{ $content = mustMerge $content $current }}

  # Recursively process if there is a request for inheritance
  {{ if and $inheritPath (not (has $inheritPath $processed)) ($files.Get $inheritPath) }}
    # File exists and is not cyclic
    {{ include "recursiveLoad" (dict "filePath" $inheritPath "files" $files "processed" $processed "content" $content) }}
  {{ else }}
    # Done
    {{- $content | toYaml | nindent 2}}
  {{ end }}
{{ end }}

{{ define "jsonConfigs" }}
{{- $files := .Files }}
{{- $overrides := .Values.configOverrides | default dict }}
{{- range (.Values.connectors | sortAlpha) }}
s3-sink-{{ . }}-configs.json: |
  {{- $configFile := printf "config/%s.yaml" . }}
  {{- $configOverrides := get $overrides . | default dict }}
  {{- $configValues := include "recursiveLoad" (dict "filePath" $configFile "files" $files "content" $configOverrides) }}
  {{- fromYaml $configValues | toPrettyJson | nindent 2 }}
{{- end }}
{{ end }}
