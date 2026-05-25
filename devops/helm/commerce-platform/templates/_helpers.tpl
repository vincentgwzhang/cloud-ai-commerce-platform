{{/*
Commerce platform chart helpers (copied from devops/k8s manifests, templated).
*/}}
{{- define "commerce-platform.namespace" -}}
{{- .Values.namespace | default "default" }}
{{- end }}

{{- define "commerce-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "commerce-platform.serviceLabels" -}}
app: {{ .appName }}
helm.sh/chart: {{ include "commerce-platform.chart" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: commerce-platform
{{- end }}

{{- define "commerce-platform.selectorLabels" -}}
app: {{ .appName }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "commerce-platform.image" -}}
{{- printf "%s:%s" .repository .tag }}
{{- end }}
