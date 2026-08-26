# Vokie release and deployment

AWS is a distribution boundary only. The Android communication path does not call AWS, S3, CloudFront, or any backend.

## One-time Android signing setup

Generate a production keystore on a secure workstation and store it in an approved secret manager:

```bash
keytool -genkeypair -v -keystore vokie-release.jks \
  -alias vokie -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 vokie-release.jks > vokie-release.jks.b64
```

Configure these GitHub Actions secrets. Never commit the keystore or passwords:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow refuses to run when any is missing. The keystore is decoded only into the ephemeral runner.

## AWS infrastructure

Requirements: AWS CLI, an AWS account, and permission to create CloudFormation/IAM resources. From `infrastructure/cloudformation/vokie-download.yaml`, create the stack with a globally unique bucket name:

```bash
aws cloudformation deploy \
  --template-file infrastructure/cloudformation/vokie-download.yaml \
  --stack-name vokie-download \
  --parameter-overrides BucketName=downloads.example-vokie-name \
  --capabilities CAPABILITY_NAMED_IAM
aws cloudformation describe-stacks --stack-name vokie-download
```

The stack creates a retained, encrypted, versioned private S3 bucket, CloudFront Origin Access Control, an HTTPS-redirecting distribution, and a bucket policy that permits reads only from that distribution.

Create a GitHub OIDC provider in IAM (`token.actions.githubusercontent.com`) and a deployment role using the templates in `infrastructure/iam/`. Replace the placeholders with the AWS account, bucket, and distribution values. The trust policy is restricted to `rohanesor/vokie` release tags (`v*`). Attach only the generated deployment policy; do not grant AdministratorAccess.

## GitHub configuration

Repository/environment configuration:

- Secret `AWS_DEPLOY_ROLE_ARN`
- Variables `AWS_REGION`, `VOKIE_BUCKET`, `VOKIE_CLOUDFRONT_DISTRIBUTION_ID`
- Android signing secrets above
- Secret `VOKIE_MODELS_ARCHIVE_URL`: short-lived/protected CI-only URL for the verified model ZIP
- Secret `VOKIE_MODELS_ARCHIVE_SHA256`: SHA-256 of that ZIP

The release job uses GitHub OIDC and has `id-token: write`; no long-lived AWS access key is used.

## Bundled offline model archive

Production builds are self-contained. The protected archive is not a GitHub repository asset and must contain `models/manifest.json`, `stt/ggml-tiny-q5_1.bin`, and `model.onnx` plus `tokens.txt` for each of `eng`, `hin`, `guj`, `mar`, `kan`, `mal`, `tam`, `tel`, `ory`, and `ben`. The manifest lists an exact SHA-256 and byte size for every one of those 21 files.

`scripts/stage-bundled-models.py` rejects an incomplete or mismatched archive before Gradle runs. The release workflow then verifies the final APK contains every model byte-for-byte and stores those entries uncompressed. On first launch Vokie atomically extracts and re-verifies APK assets to private storage; extraction has no network or import path.

## Release flow

```bash
git checkout main
git pull
git tag v0.1.0
git push origin v0.1.0
```

The tag workflow tests, lints, assembles the signed release, computes SHA-256, publishes immutable `downloads/v0.1.0/` artifacts and mutable `downloads/latest/` aliases, uploads the static website, invalidates CloudFront, and creates a GitHub Release.

Versioned artifacts are never overwritten. The workflow fails if the versioned APK already exists.

## Local deployment verification

With AWS credentials that are intentionally allowed to deploy:

```bash
AWS_REGION=... VOKIE_BUCKET=... VOKIE_CLOUDFRONT_DISTRIBUTION_ID=... \
  scripts/deploy-aws.sh release/Vokie-v0.1.0.apk 0.1.0 "$(git rev-parse HEAD)"
```

This script validates the APK name, calculates SHA-256, refuses an existing versioned object, uploads the version and latest aliases, generates website `release.json`, uploads the site, invalidates CloudFront, and verifies both website and APK objects.

No AWS deployment is considered successful until the script exits zero and the CloudFront URL serves the uploaded files.
