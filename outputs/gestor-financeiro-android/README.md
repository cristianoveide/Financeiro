# Gestor Financeiro Android

Na primeira abertura, após desbloquear o app, o Android mostra a tela oficial de login/autorização do Google. Confirme a conta e conceda acesso ao Drive. O app não pede nem recebe sua senha, não mostra seletor de pasta e armazena `gestor-financeiro-backup.json` na área privada do app no Drive (`appDataFolder`), oculta da lista normal de arquivos.

Cada alteração é sincronizada automaticamente quando há conexão. Ao iniciar, o app confere se existe uma cópia mais nova no Drive e restaura quando for seguro. Se telefone e Drive tiverem alterações diferentes, preserva ambas e pergunta qual manter. Toque no indicador do topo para entrar de novo ou renovar a autorização.

## Configuração OAuth necessária

O projeto Google Cloud usado para distribuir o APK precisa ter a Google Drive API ativada, a tela de consentimento OAuth configurada e um cliente OAuth Android com o pacote `br.com.gestorfinanceiro` e o SHA-1 do certificado de assinatura correspondente. O artefato gerado pelo workflow é de depuração e usa certificado temporário; configure o cliente para a assinatura de produção antes de distribuir. Sem essa configuração, o APK compila, mas não consegue autenticar nem sincronizar.
