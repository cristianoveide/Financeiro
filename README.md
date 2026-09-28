# Gestor Financeiro Android

Aplicativo Android para o Gestor Financeiro com comandos por voz e texto. O projeto Android está em `outputs/gestor-financeiro-android/android`; o servidor Node.js e a interface web ficam em `outputs/gestor-financeiro-android`.

## Publicar o servidor

Este repositório inclui `render.yaml` para criar o serviço Node no Render. Crie um Blueprint a partir deste repositório. No Render, forneça `AIRTABLE_TOKEN` como segredo, com os escopos `data.records:read` e `data.records:write`, autorizado à base `appj4RydYWjdUuD5o`. Não salve o token no código nem no APK.

Quando o deploy terminar, copie a URL HTTPS gerada pelo Render e informe essa URL no app Android em **Servidor**. Teste `https://SUA-URL/api/status`; o retorno deve conter `"connected":true`.

## Gerar o APK

No Windows com Android Studio instalado, execute `outputs/gestor-financeiro-android/Build-Apk.cmd`. O APK de depuração sai em `outputs/gestor-financeiro-android/android/app/build/outputs/apk/debug/app-debug.apk`.