# UltraTel — assinatura e compilação pelo GitHub Actions

Este fork é um **cliente não oficial** do Telegram. O Firebase está registrado sob
o pacote Android `com.ultratel.messenger`.

O APK **não deve ser instalado ou distribuído** até que as credenciais próprias,
a assinatura e os demais componentes necessários tenham sido verificados.
Nunca ignore alertas de segurança apenas porque o APK compilou.

## 1. Criar assinatura Android no Termux (uma única vez)

Execute **no seu próprio celular**, não no GitHub nem em uma conversa:

```bash
pkg update
pkg install openjdk-17 coreutils
mkdir -p ~/ultratel-signing
cd ~/ultratel-signing

keytool -genkeypair \
  -keystore ultratel-release.keystore \
  -storetype PKCS12 \
  -alias ultratel \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000 \
  -dname "CN=UltraTel, OU=Android, O=UltraTel, C=BR"
```

Crie uma senha forte, anote-a em local seguro e use a **mesma senha**
para o repositório do certificado e para a chave privada.
A configuração do projeto espera o alias exato `ultratel`.

**Guarde uma cópia de segurança** de `ultratel-release.keystore` e da senha.
Perder essa chave pode impedir que versões futuras atualizem o mesmo app instalado.

Gere o texto Base64 que será inserido em um secret do GitHub:

```bash
base64 -w 0 ultratel-release.keystore > ultratel-keystore-base64.txt
```

O arquivo Base64 **é a chave privada codificada, não criptografada**.
Não o publique no Git, em Issues ou em chats; copie-o apenas para o secret.

## 2. Criar quatro GitHub Actions Secrets

No repositório `elenilda10/Telegram`:

**Settings → Secrets and variables → Actions → New repository secret**

| Nome do secret | Valor |
| --- | --- |
| `ULTRATEL_API_ID` | Seu API ID de https://my.telegram.org |
| `ULTRATEL_API_HASH` | Seu API hash de https://my.telegram.org |
| `ULTRATEL_KEYSTORE_B64` | Conteúdo inteiro de `ultratel-keystore-base64.txt` |
| `ULTRATEL_KEYSTORE_PASSWORD` | Senha escolhida no `keytool` |

Nunca registre secrets em `gradle.properties`, `BuildVars.java` ou arquivos
enviados à revisão de código. No GitHub Actions, a etapa
`scripts/prepare_ultratel_build.py` aplica os valores **somente no runner**.
O API ID e o API hash são incorporados ao aplicativo compilado e podem ser
extraídos do APK: secrets protegem o repositório e os logs, não o binário.

## 3. Compilar depois de configurar os secrets

No GitHub, abra **Actions → Android APK - teste** (nome ainda exibido na branch principal) **→ Run workflow**,
selecionando a branch `feature/ultratel-firebase-20261113`
enquanto o PR ainda estiver em rascunho.

O workflow deve falhar **antes de instalar os SDKs** caso um secret esteja ausente
ou a assinatura seja inválida. Quando tudo estiver configurado, a compilação
executa `assembleAfatRelease` e, se concluída, gera o artefato
`ultratel-android-release`.

## 4. Verificações antes de uso real

- A instalação não deve ser forçada se o telefone apontar malware.
- Verificar assinatura, nome do pacote e ícone próprio.
- Verificar login e chamadas à API usando as credenciais próprias.
- Verificar Firebase Cloud Messaging; o JSON sozinho não garante o recebimento
  de notificações.
- Revisar integrações que usam credenciais oficiais do Telegram, links oficiais,
  analytics e funcionalidades dependentes de Google/Huawei.
- Não distribuir APKs usando a chave de teste que veio no repositório original.
