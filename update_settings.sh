#!/bin/bash
sed -i '/switch_browser_autodetect/a \
\
                    HorizontalDivider(\
                        modifier = Modifier.padding(vertical = 12.dp),\
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)\
                    )\
\
                    BrowserSettingSwitchRow(\
                        title = "Télécharger via Wi-Fi uniquement",\
                        description = "Met en pause les téléchargements si vous êtes sur réseau mobile",\
                        checked = downloadSettings.wifiOnly,\
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(wifiOnly = it.wifiOnly.not()) } },\
                        testTag = "switch_wifi_only"\
                    )\
\
                    HorizontalDivider(\
                        modifier = Modifier.padding(vertical = 12.dp),\
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)\
                    )\
\
                    BrowserSettingSwitchRow(\
                        title = "Reprise automatique",\
                        description = "Tente de relancer automatiquement un téléchargement échoué",\
                        checked = downloadSettings.autoRetry,\
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(autoRetry = it.autoRetry.not()) } },\
                        testTag = "switch_auto_retry"\
                    )\
' app/src/main/java/com/example/ui/screens/SettingsScreen.kt
