# Changelog

## [0.4.0](https://github.com/shoriminimoe/beans/compare/v0.3.0...v0.4.0) (2026-05-22)


### Features

* sort transactions newest-first in the ledger and register ([317141f](https://github.com/shoriminimoe/beans/commit/317141f9073c656ab32fe4a8acc97388c3d9fb17))
* sort transactions newest-first in the ledger and register ([efc54ac](https://github.com/shoriminimoe/beans/commit/efc54acee241280b0c1d309555a916bf9e32cc00)), closes [#17](https://github.com/shoriminimoe/beans/issues/17)
* type-to-filter account selector on the register screen ([ae4592a](https://github.com/shoriminimoe/beans/commit/ae4592a6b4ee6a2af3c1ff6e28ba5278aa1a4cfe))
* type-to-filter account selector on the register screen ([0fed45b](https://github.com/shoriminimoe/beans/commit/0fed45bb5a0036c9dd054b16e1ad0dd91b940e26)), closes [#18](https://github.com/shoriminimoe/beans/issues/18)


### Bug Fixes

* keep caret at end after applying an autocomplete suggestion ([b615a96](https://github.com/shoriminimoe/beans/commit/b615a963a86d5166226df21ac36f657e37817581))
* keep caret at end after applying an autocomplete suggestion ([cb7a064](https://github.com/shoriminimoe/beans/commit/cb7a064c6f48630bbba11f4e5476b7fad75f6198)), closes [#19](https://github.com/shoriminimoe/beans/issues/19)

## [0.3.0](https://github.com/shoriminimoe/beans/compare/v0.2.0...v0.3.0) (2026-05-21)


### Features

* add ledger switcher to return to file selection ([2baaa7c](https://github.com/shoriminimoe/beans/commit/2baaa7c419249a1614c34a2d3a7625fdd6c0fce7))
* add ledger switcher to return to file selection ([36467a0](https://github.com/shoriminimoe/beans/commit/36467a044ee281aa2404ceb07a994043f75a5f06)), closes [#6](https://github.com/shoriminimoe/beans/issues/6)
* show app version on the file picker screen ([98e4470](https://github.com/shoriminimoe/beans/commit/98e44708e9004b3068d6eba95551819291d1f7b5))
* show app version on the file picker screen ([2e8be50](https://github.com/shoriminimoe/beans/commit/2e8be505d9fd44a06b44e7702e3eea917453ff66)), closes [#8](https://github.com/shoriminimoe/beans/issues/8)


### Bug Fixes

* keep last transaction clear of the "+" button ([8af3826](https://github.com/shoriminimoe/beans/commit/8af3826aca795a721a0135a14c749c18a2ef3e53))
* keep last transaction clear of the "+" button ([d43c1f1](https://github.com/shoriminimoe/beans/commit/d43c1f10f5a5c2dbd958c993999423f3fe714d4a)), closes [#7](https://github.com/shoriminimoe/beans/issues/7)
* reset register state when switching ledgers ([83044d3](https://github.com/shoriminimoe/beans/commit/83044d3c842b90721dbdaf91cfeb389c6f3709cc))
* show only versionName, not the always-1 versionCode ([5dc5a55](https://github.com/shoriminimoe/beans/commit/5dc5a550e89e938829f5822b768a8ba8a22490c4))
* sort currencies consistently in register rows ([8274429](https://github.com/shoriminimoe/beans/commit/8274429a362e682b6e434995e3228a44446a1927))
* sort currencies consistently in register rows ([a953d70](https://github.com/shoriminimoe/beans/commit/a953d7008ae5a81ee3e9dad3354ab5dd3cb30885)), closes [#1](https://github.com/shoriminimoe/beans/issues/1)


### Performance Improvements

* share a single date formatter across ledger rows ([63728c6](https://github.com/shoriminimoe/beans/commit/63728c6fc401239d5694fc78bfeb8e6f21edd633))
* share a single date formatter across ledger rows ([de1af7a](https://github.com/shoriminimoe/beans/commit/de1af7a6f72b9f2a0c025c61bf6dd95f1019197d)), closes [#5](https://github.com/shoriminimoe/beans/issues/5)

## [0.2.0](https://github.com/shoriminimoe/beans/compare/v0.1.0...v0.2.0) (2026-05-21)


### Features

* add computeRegister to BalanceCalculator ([00dd2c6](https://github.com/shoriminimoe/beans/commit/00dd2c62a8fd80a4330f84279c7339317ff54f6d))
* add RegisterScreen UI ([2b78486](https://github.com/shoriminimoe/beans/commit/2b78486f1874e6f15c324e236e2ab5651461f0cb))
* add RegisterViewModel ([f21f6e2](https://github.com/shoriminimoe/beans/commit/f21f6e238423e7b01f0ad2a27de1caf4c05661ba))
* add selectableAccounts to BalanceCalculator ([5d07e46](https://github.com/shoriminimoe/beans/commit/5d07e46a39bf85e7f953c3b4c5478538d4e641d4))
* wire register view into navigation ([fad1a1c](https://github.com/shoriminimoe/beans/commit/fad1a1cf1dffbc58c9636d34467d8319ea0b52fa))


### Bug Fixes

* guard empty balance column in register row ([03f1ecf](https://github.com/shoriminimoe/beans/commit/03f1ecffa4af378597fb4985f7b7dc7397b79b44))
* refresh register entries when reloading accounts ([89f479e](https://github.com/shoriminimoe/beans/commit/89f479ea4025560108381c5531674cf6af1bde9a))
