# Gestor Financeiro

O APK Android guarda os dados no SQLite privado do telefone e sincroniza automaticamente um backup no Google Drive. No primeiro uso, o Android abre a tela oficial do Google para escolher/confirmar a conta e autorizar o acesso. O app nunca solicita nem armazena a senha do Google e não abre um seletor de pastas. O backup fica na área privada do app no Drive (`appDataFolder`), invisível na lista normal de arquivos.

Cada alteração é sincronizada quando há conexão. Ao iniciar, o app compara as cópias e restaura a do Drive se ela for a única que mudou. Se telefone e Drive tiverem alterações diferentes, as duas são preservadas e o app pede qual manter. O indicador no topo permite concluir ou renovar o login.

## Configurar o Google Drive

Antes de distribuir o APK, configure um projeto no Google Cloud: ative a Google Drive API, configure a tela de consentimento OAuth e registre um cliente OAuth Android para o pacote `br.com.gestorfinanceiro` com o SHA-1 do certificado que assina o APK distribuído. O artefato do workflow é de depuração, assinado com certificado temporário, e não serve como certificado de produção. Sem o cliente OAuth correspondente, o APK compila, mas o login e o backup não funcionam.

## Publicar o servidor

Este repositório inclui `render.yaml` para criar o serviço Node no Render. Crie um Blueprint a partir deste repositório e forneça `AIRTABLE_TOKEN` como segredo, com os escopos `data.records:read` e `data.records:write`, autorizado à base `appj4RydYWjdUuD5o`. Não salve o token no código nem no APK.

Quando o deploy terminar, copie a URL HTTPS gerada pelo Render e informe-a no app web em **Servidor**. Teste `https://SUA-URL/api/status`; o retorno deve conter `"connected":true`.

## Gerar o APK

No Windows com Android Studio instalado, execute `outputs/gestor-financeiro-android/Build-Apk.cmd`. O APK de depuração sai em `outputs/gestor-financeiro-android/android/app/build/outputs/apk/debug/app-debug.apk`. O workflow `Android APK` também compila o app e publica o artefato `gestor-financeiro-android-v2.5`.

Os arquivos de servidor Node, configuração do Render e integração Airtable foram preservados, mas o APK local não depende deles. Os dados desses serviços permanecem separados do banco do telefone.
