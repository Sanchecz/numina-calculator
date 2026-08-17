# Security Policy

## Supported versions

Only the latest published Numina version receives security fixes.

## Reporting a vulnerability

Do not disclose exploitable vulnerabilities in a public issue. The publisher must configure a private security-reporting channel or GitHub Private Vulnerability Reporting before public release and replace this paragraph with the monitored contact and response SLA.

Include the affected version, Android version, reproduction steps, impact and a minimal proof of concept. Do not include real user data or signing secrets.

## Security properties

- no Android permissions and no networking;
- no WebView, native code, dynamic loading, reflection-based expression execution or scripting engine;
- bounded mathematical parser with typed errors;
- only launcher Activity is exported;
- cleartext traffic is disabled defensively;
- calculation history is excluded from backup;
- release is non-debuggable, R8-minified and resource-shrunk;
- signing secrets are read only from environment variables and are ignored by Git.

## Release security checklist

- run unit, lint and instrumented tests without baselines or ignored failures;
- review dependency changes and Gradle verification metadata;
- verify APK signer certificate and permissions;
- retain R8 mapping for each release;
- use Google Play App Signing with a separately protected upload key;
- use least-privilege CI permissions and protected release environments.
