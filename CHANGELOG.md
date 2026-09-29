## [1.4.9](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.8...v1.4.9) (2026-09-29)

### 🐛 Bug Fixes

* safely restore MATCH_ALL flag after move-result-object ([1feb38f](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/1feb38f077ef6818c4e47c16b0e9c51b98804be2))

## [1.4.8](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.7...v1.4.8) (2026-09-29)

### 🐛 Bug Fixes

* hardcode target package to bypass CLI config cache ([0e34fd4](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/0e34fd4160c48ae6a838a89e753675e2454e79b1))
* revert boolean flag bypass that causes startup crash ([5a28b5e](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/5a28b5e4cb17597d19c6aac2dce465a9db20cd20))

## [1.4.7](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.6...v1.4.7) (2026-09-29)

### 🐛 Bug Fixes

* use boolean flag bypass to safely allow Morphe YT Music ([f35a6cf](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/f35a6cfde584a470acfcd38716160037020f248c))

## [1.4.6](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.5...v1.4.6) (2026-09-29)

### 🐛 Bug Fixes

* enforce MATCH_ALL flag safely with register restoration ([bc66f10](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/bc66f109bbf3bb99d0d322c28f6e6fa7f6cbf133))

## [1.4.5](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.4...v1.4.5) (2026-09-29)

### 🐛 Bug Fixes

* revert queryIntentServices MATCH_ALL injection ([6daf2cb](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/6daf2cb346c69d38eeba491c40d88116e5a775c6))

## [1.4.4](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.3...v1.4.4) (2026-09-29)

### 🐛 Bug Fixes

* enforce MATCH_ALL flag in queryIntentServices to fix Android 11+ visibility for YT Music ([272d966](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/272d966dcd314fe4fe19551d37e41ec7130e109f))
* use FiveRegisterInstruction instead of Instruction35c to fix build ([6d9a2c3](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/6d9a2c3432eebffc816d74eec6c52e1ccb7ef2ad))

## [1.4.3](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.2...v1.4.3) (2026-09-29)

### 🐛 Bug Fixes

* actually revert spotify bypass to trigger release ([cadce2b](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/cadce2bfaa644410e35737d11a6084213afcad31))
* revert RestoreMapDataPatch crash fixes that break v1.4.0 ([ebf1827](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/ebf1827c0880b01a2a25acd32191e8d417d8dff9))

## [1.4.2](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.1...v1.4.2) (2026-09-29)

## [1.4.1](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.4.0...v1.4.1) (2026-09-29)

### 🐛 Bug Fixes

* add missing replaceInstruction import in RestoreMapDataPatch ([5ed2970](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/5ed2970355a6f303ff89408b0d480a9c52814a06))
* apply known crash fixes to v1.4.0 RestoreMapDataPatch ([c68818b](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/c68818bd6590c172732092f71e84d2316ed1f74c))
* refine media provider bypass to include Spotify without causing duplicates ([6f2ec06](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/6f2ec06af32014757b4c1b6f106b8eeef859e7c3))

## [1.4.0](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.3.1...v1.4.0) (2026-09-28)

### ✨ New Features

* **maps:** add package renaming and MicroG spoofing patches ([c9b0b2d](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/c9b0b2d552bf39749d071ddb260067665a2845d5))

## [1.3.1](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.3.0...v1.3.1) (2026-09-27)

### 🐛 Bug Fixes

* **maps:** resolve crash by preventing duplicate keys in media provider map ([8576bf0](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/8576bf062aab405be3954fc5faf25bfb833663bd))

## [1.3.0](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.2.0...v1.3.0) (2026-09-27)

### ✨ New Features

* **maps:** comprehensive bypass for navigation media provider resolution ([d857279](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/d857279894362da5cd7216fcb26a85444116ef97))

## [1.2.0](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.1.0...v1.2.0) (2026-09-27)

### ✨ New Features

* **maps:** force phenotype media feature flag to true and allow apkm bundles ([73ceb0f](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/73ceb0fa368eb8fb47a976f631b49e91186c8aaf))

## [1.1.0](https://github.com/bartlomiejfornalczyk/morphe-patches/compare/v1.0.0...v1.1.0) (2026-09-27)

### ✨ New Features

* add manifest package visibility and bypass server flag for Google Maps YouTube Music ([18a19cf](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/18a19cfc51cf94f631770ac2566e164ab9c026cd))

## 1.0.0 (2026-09-27)

### ✨ New Features

* update patches-bundle manifest for v1.0.0 ([dfb83f8](https://github.com/bartlomiejfornalczyk/morphe-patches/commit/dfb83f817257f08605739e41cd75d1b5f244f64c))
