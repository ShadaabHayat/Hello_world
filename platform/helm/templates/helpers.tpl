{{- define "configmapChecksum" -}}
{{- $configData := (include (print $.Template.BasePath "/connector-configmap.yaml") $) | sha256sum -}}
{{- $configData | trunc 8 -}}
{{- end -}}
