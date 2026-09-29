# Gestor Financeiro Offline para Android

O APK abre a interface incluída no próprio app e funciona sem Render, Airtable ou internet. Os dados ficam no SQLite privado do aplicativo no telefone. O app mantém tema escuro azul, bloqueio biométrico, comandos por texto e voz, lançamentos, categorias, consultas, correções e exclusões com confirmação.

## Copiar seus dados atuais

No Airtable, exporte cada tabela como CSV e transfira os arquivos ao telefone. No app, abra **Seus dados**, escolha **Importar CSV / JSON**, selecione a tabela do arquivo e importe. Repita para cada tabela. A confirmação apresenta o conteúdo a importar; só então o app copia os registros para o SQLite local. A importação acrescenta dados, evita duplicatas ao reimportar o mesmo arquivo e nunca escreve no Airtable ou Render.

Também é possível importar JSON no formato de backup do app ou registros Airtable (`records` com `fields`). **Exportar backup** cria um arquivo JSON por meio do seletor de documentos Android. Guarde o backup em local seguro. O Android não restaura automaticamente o banco após a desinstalação, e desinstalar/limpar os dados remove a cópia local.

## Voz e segurança

O app solicita processamento de voz offline, quando suportado pelo serviço de reconhecimento instalado no dispositivo e pelo pacote de português baixado. Se o aparelho não reconhecer fala offline, use os comandos por texto. A biometria ou credencial de tela é solicitada ao abrir. O banco fica no armazenamento privado do app e o backup Android está desativado. A permissão Android de internet foi removida.

## Gerar o APK

Execute `Build-Apk.cmd` no Windows com Android Studio instalado. O APK de depuração é salvo em `android/app/build/outputs/apk/debug/app-debug.apk`. O GitHub Actions também compila e publica o APK como artefato.

Os arquivos `server.mjs`, `render.yaml` e o fluxo do Airtable continuam no repositório como componentes online antigos; o APK offline não depende deles. Nenhum dado remoto foi consultado, alterado ou excluído durante a migração do aplicativo.
