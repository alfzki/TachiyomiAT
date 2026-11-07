<div align="center">  

<a href="https://github.com/alfzki/TachiyomiAT">  
    <img src="./.github/assets/logo.png" alt="TachiyomiAT logo" title="TachiyomiAT logo" width="80"/>  
</a>  

# TachiyomiAT [App](#)

> Rebased onto Mihon v0.21.3 with Metro DI, AndroidX ViewModel, Jetpack Compose, and Moko resources.

### Full-featured reader with Automatic Manga Translations
Discover, translate and read manga, webtoons, comics, and more – easier than ever on your Android device.

[![Discord server](https://img.shields.io/discord/801338287012053042.svg?label=&labelColor=6A7EC2&color=7389D8&logo=discord&logoColor=FFFFFF)](https://discord.com/invite/rkvXfVPRdq)
[![GitHub downloads](https://img.shields.io/github/downloads/alfzki/TachiyomiAT/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/alfzki/TachiyomiAT/releases)  
[![License: Apache-2.0](https://img.shields.io/github/license/alfzki/TachiyomiAT?labelColor=27303D&color=0877d2)](/LICENSE)  
[![Translation status](https://img.shields.io/weblate/progress/mihon?labelColor=27303D&color=946300)](https://hosted.weblate.org/engage/mihon/)

[![Discord Banner 3](https://discord.com/api/guilds/801338287012053042/widget.png?style=banner3)](https://discord.com/invite/rkvXfVPRdq)
## Download

[Releases](https://github.com/alfzki/TachiyomiAT/releases)

*Requires Android 8.0 or higher.*

## Features

<div align="left">  

* All features of the latest version of [Mihon](https://github.com/mihonapp/mihon)
* Translate chapters into any language
* Quick toggle button in the reader toolbar to easily show or hide translations
* Overlay rendering for both Pager and Webtoon reader viewers
* Choose fonts you like (Anime Ace, Comic Book, Manga Master BB)
* Profile-based rendering (CJK & Latin typography) and interactive translation bubbles (tap to inspect, double-tap to delete)
* In-reader Translation Settings tab for real-time adjustments
* Multiple translation engines: On-device MLKit, Google Translate, Gemini AI (default `gemini-3.5-flash-lite`), OpenRouter, and Google Cloud Translation
</div>  

## Supported Languages

<div align="left">

* Chinese (trad/sim)
* Korean
* Japanese
* Latin / English
* **Auto Detect** (automatically detects script & language)

> You can translate from these to any other language.
</div>  

## Available Translators
<div align="left">

* MLKit : On-Device Translations
* Google Translate : Web-Based
* Cloud Translation : Google Cloud Translation API
* Gemini : [Get API Key Here](https://makersuite.google.com/app/apikey "Get API Key Here") (Default model: `gemini-3.5-flash-lite`)
* OpenRouter: [Get API Key Here](https://openrouter.ai/ "Get API Key Here")

</div>  

## Translation Comparison

Source: Tian Zuan : Chapter 615
| Gemini | Google Translate | MLKit (On Device) | Raw |
| ------------- | ------------- | ------------- | ------------- |
| ![ManhuaPlus](./.github/assets/gemini.png) | ![Gemini](./.github/assets/gt.png) | ![GoogleTranslate](./.github/assets/mlkit.png) | ![mlkit](./.github/assets/raw.png) |


## Before You Begin

<div align="left">

1. You can translate a chapter after downloading it; a translation indicator button appears next to the download button.
2. For API-based translators, add your own API key in Settings -> Translations.
3. Select the appropriate source and target language in Settings -> Translations.
4. For OpenRouter translator, provide the model name in settings (default: `google/gemma-2-9b-it:free`).

</div>  

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Before reporting a new issue, check already opened [issues](https://github.com/alfzki/TachiyomiAT/issues); if you have any questions, join the [Discord server](https://discord.com/invite/rkvXfVPRdq).

### Repositories

[![alfzki/TachiyomiAT - GitHub](https://github-readme-stats.vercel.app/api/pin/?username=alfzki&repo=TachiyomiAT&bg_color=161B22&text_color=c9d1d9&title_color=0877d2&icon_color=0877d2&border_radius=8&hide_border=true)](https://github.com/alfzki/TachiyomiAT)

### Credits

Thank you to all the people who have contributed!

<a href="https://github.com/alfzki/TachiyomiAT/graphs/contributors">  
    <img src="https://contrib.rocks/image?repo=alfzki/TachiyomiAT" alt="TachiyomiAT contributors" title="TachiyomiAT contributors" width="800"/>  
</a>  

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>  
Copyright © 2015 Javier Tomás  
  
Licensed under the Apache License, Version 2.0 (the "License");  
you may not use this file except in compliance with the License.  
You may obtain a copy of the License at  
  
http://www.apache.org/licenses/LICENSE-2.0  
  
Unless required by applicable law or agreed to in writing, software  
distributed under the License is distributed on an "AS IS" BASIS,  
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
See the License for the specific language governing permissions and  
limitations under the License.  
</pre>  

</div>
