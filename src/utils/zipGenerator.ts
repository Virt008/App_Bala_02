import JSZip from 'jszip';

// Importa o conteúdo real diretamente dos arquivos-fonte do projeto Android
// para garantir 100% de sincronismo com todas as correções aplicadas.
import buildGradleKts from '../../build.gradle.kts?raw';
import settingsGradleKts from '../../settings.gradle.kts?raw';
import gradleProperties from '../../gradle.properties?raw';
import libsVersionsToml from '../../gradle/libs.versions.toml?raw';
import appBuildGradleKts from '../../app/build.gradle.kts?raw';
import androidManifestXml from '../../app/src/main/AndroidManifest.xml?raw';
import stringsXml from '../../app/src/main/res/values/strings.xml?raw';
import themesXml from '../../app/src/main/res/values/themes.xml?raw';
import backupRulesXml from '../../app/src/main/res/xml/backup_rules.xml?raw';
import dataExtractionRulesXml from '../../app/src/main/res/xml/data_extraction_rules.xml?raw';
import mainActivityKt from '../../app/src/main/java/com/balanca/assistente/MainActivity.kt?raw';
import productKt from '../../app/src/main/java/com/balanca/assistente/data/Product.kt?raw';
import recentProductKt from '../../app/src/main/java/com/balanca/assistente/data/RecentProduct.kt?raw';
import productDaoKt from '../../app/src/main/java/com/balanca/assistente/data/ProductDao.kt?raw';
import appDatabaseKt from '../../app/src/main/java/com/balanca/assistente/data/AppDatabase.kt?raw';
import textNormalizerKt from '../../app/src/main/java/com/balanca/assistente/util/TextNormalizer.kt?raw';
import csvHelperKt from '../../app/src/main/java/com/balanca/assistente/util/CsvHelper.kt?raw';
import imageHelperKt from '../../app/src/main/java/com/balanca/assistente/util/ImageHelper.kt?raw';
import themeKt from '../../app/src/main/java/com/balanca/assistente/ui/theme/Theme.kt?raw';
import productViewModelKt from '../../app/src/main/java/com/balanca/assistente/ui/ProductViewModel.kt?raw';
import scaleAssistantAppKt from '../../app/src/main/java/com/balanca/assistente/ui/ScaleAssistantApp.kt?raw';
import gradleWrapperProperties from '../../gradle/wrapper/gradle-wrapper.properties?raw';
import githubWorkflow from '../../.github/workflows/android-build.yml?raw';
import readmeMd from '../../android/README.md?raw';

export interface AndroidFileRecord {
  path: string;
  content: string;
}

export const ANDROID_FILES: AndroidFileRecord[] = [
  { path: 'build.gradle.kts', content: buildGradleKts },
  { path: 'settings.gradle.kts', content: settingsGradleKts },
  { path: 'gradle.properties', content: gradleProperties },
  { path: 'gradle/libs.versions.toml', content: libsVersionsToml },
  { path: 'app/build.gradle.kts', content: appBuildGradleKts },
  { path: 'app/src/main/AndroidManifest.xml', content: androidManifestXml },
  { path: 'app/src/main/res/values/strings.xml', content: stringsXml },
  { path: 'app/src/main/res/values/themes.xml', content: themesXml },
  { path: 'app/src/main/res/xml/backup_rules.xml', content: backupRulesXml },
  { path: 'app/src/main/res/xml/data_extraction_rules.xml', content: dataExtractionRulesXml },
  { path: 'app/src/main/java/com/balanca/assistente/MainActivity.kt', content: mainActivityKt },
  { path: 'app/src/main/java/com/balanca/assistente/data/Product.kt', content: productKt },
  { path: 'app/src/main/java/com/balanca/assistente/data/RecentProduct.kt', content: recentProductKt },
  { path: 'app/src/main/java/com/balanca/assistente/data/ProductDao.kt', content: productDaoKt },
  { path: 'app/src/main/java/com/balanca/assistente/data/AppDatabase.kt', content: appDatabaseKt },
  { path: 'app/src/main/java/com/balanca/assistente/util/TextNormalizer.kt', content: textNormalizerKt },
  { path: 'app/src/main/java/com/balanca/assistente/util/CsvHelper.kt', content: csvHelperKt },
  { path: 'app/src/main/java/com/balanca/assistente/util/ImageHelper.kt', content: imageHelperKt },
  { path: 'app/src/main/java/com/balanca/assistente/ui/theme/Theme.kt', content: themeKt },
  { path: 'app/src/main/java/com/balanca/assistente/ui/ProductViewModel.kt', content: productViewModelKt },
  { path: 'app/src/main/java/com/balanca/assistente/ui/ScaleAssistantApp.kt', content: scaleAssistantAppKt },
  { path: 'gradle/wrapper/gradle-wrapper.properties', content: gradleWrapperProperties },
  { path: '.github/workflows/android-build.yml', content: githubWorkflow },
  { path: 'README.md', content: readmeMd },
];

/**
 * Empacota todos os arquivos do projeto Android em um arquivo .ZIP pronto para download
 */
export async function downloadAndroidProjectZip(): Promise<void> {
  const zip = new JSZip();

  for (const file of ANDROID_FILES) {
    zip.file(file.path, file.content);
  }

  const content = await zip.generateAsync({ type: 'blob' });
  const url = URL.createObjectURL(content);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', 'assistente-de-balanca-android-projeto.zip');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
