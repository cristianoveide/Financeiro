# Gestor Financeiro Offline para Android

O APK guarda lançamentos, categorias, contas e cartões no SQLite privado do telefone. Toque no indicador do topo para entrar na conta Google (se necessário) e escolher uma pasta do Drive. O app cria e atualiza automaticamente nessa pasta o arquivo `gestor-financeiro-backup.json`; a interface não oferece importação nem exportação manual.

Cada alteração é sincronizada quando há conexão. Ao abrir, o app confere se a cópia do Drive mudou e restaura se ela for a única versão alterada. Se telefone e Drive tiverem alterações diferentes, o app preserva ambas e pede qual manter. Em outro aparelho, conecte a mesma conta Google e escolha a pasta que contém o arquivo para sincronizar.

## Voz e segurança

O app solicita processamento de voz offline quando suportado pelo serviço instalado e pelo pacote de português baixado. A biometria ou credencial de tela é solicitada ao abrir. O banco fica no armazenamento privado do app. O acesso ao Drive usa o provedor de documentos Android após autorização da pasta pelo usuário.

## Gerar o APK

Execute `Build-Apk.cmd` no Windows com Android Studio instalado. O APK de depuração é salvo em `android/app/build/outputs/apk/debug/app-debug.apk`. O GitHub Actions também compila e publica o APK como artefato.

Os componentes antigos `server.mjs`, `render.yaml` e integração Airtable continuam no repositório, mas o APK local não depende deles.
