# Libraries inside the app

Every library that ends up in the release build, including the ones
the app's own dependencies bring with them, and the licence each is
published under. Made by `tools/licences.py --write` from Gradle's
dependency list; do not edit by hand.

In all: **115 libraries**, 114 under Apache-2.0, 1 under BSD-3-Clause.
Both licences allow use in an Apache-2.0 app. The BSD-3-Clause one asks
that its notice travel with the app: it is inside the APK, and a copy is
in [licenses/protobuf-BSD-3-Clause.txt](../licenses/protobuf-BSD-3-Clause.txt).

Not libraries, but also bundled: the Inter typeface (SIL Open Font
License 1.1) and the Exodus Privacy tracker list (Open Database License
1.0). See [licenses/README.md](../licenses/README.md).

| Library | Version | Licence |
|---|---|---|
| `androidx.activity:activity` | 1.13.0 | Apache-2.0 |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 |
| `androidx.activity:activity-ktx` | 1.13.0 | Apache-2.0 |
| `androidx.annotation:annotation` | 1.10.0 | Apache-2.0 |
| `androidx.annotation:annotation-experimental` | 1.4.1 | Apache-2.0 |
| `androidx.annotation:annotation-jvm` | 1.10.0 | Apache-2.0 |
| `androidx.arch.core:core-common` | 2.2.0 | Apache-2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 | Apache-2.0 |
| `androidx.autofill:autofill` | 1.0.0 | Apache-2.0 |
| `androidx.collection:collection` | 1.6.0 | Apache-2.0 |
| `androidx.collection:collection-jvm` | 1.6.0 | Apache-2.0 |
| `androidx.collection:collection-ktx` | 1.6.0 | Apache-2.0 |
| `androidx.compose:compose-bom` | 2026.09.00 | Apache-2.0 |
| `androidx.compose.animation:animation` | 1.12.1 | Apache-2.0 |
| `androidx.compose.animation:animation-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.animation:animation-core` | 1.12.1 | Apache-2.0 |
| `androidx.compose.animation:animation-core-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.foundation:foundation` | 1.12.1 | Apache-2.0 |
| `androidx.compose.foundation:foundation-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout` | 1.12.1 | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.material:material-ripple` | 1.12.1 | Apache-2.0 |
| `androidx.compose.material:material-ripple-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.material3:material3` | 1.4.0 | Apache-2.0 |
| `androidx.compose.material3:material3-android` | 1.4.0 | Apache-2.0 |
| `androidx.compose.runtime:runtime` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-retain` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-retain-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable` | 1.12.1 | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-geometry` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-graphics` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-text` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-text-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-unit` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-util` | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui:ui-util-android` | 1.12.1 | Apache-2.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 | Apache-2.0 |
| `androidx.core:core` | 1.19.1 | Apache-2.0 |
| `androidx.core:core-ktx` | 1.19.1 | Apache-2.0 |
| `androidx.core:core-viewtree` | 1.0.0 | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 | Apache-2.0 |
| `androidx.datastore:datastore` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-android` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-core` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-core-android` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-core-okio` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-core-okio-jvm` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-preferences-android` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core-android` | 1.3.0 | Apache-2.0 |
| `androidx.datastore:datastore-preferences-external-protobuf` | 1.3.0 | BSD-3-Clause |
| `androidx.datastore:datastore-preferences-proto` | 1.3.0 | Apache-2.0 |
| `androidx.emoji2:emoji2` | 1.4.0 | Apache-2.0 |
| `androidx.graphics:graphics-path` | 1.0.1 | Apache-2.0 |
| `androidx.interpolator:interpolator` | 1.0.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.11.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.11.0 | Apache-2.0 |
| `androidx.navigationevent:navigationevent` | 1.0.0 | Apache-2.0 |
| `androidx.navigationevent:navigationevent-android` | 1.0.0 | Apache-2.0 |
| `androidx.navigationevent:navigationevent-compose` | 1.0.0 | Apache-2.0 |
| `androidx.navigationevent:navigationevent-compose-android` | 1.0.0 | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.1 | Apache-2.0 |
| `androidx.savedstate:savedstate` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate:savedstate-android` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate:savedstate-compose` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate:savedstate-compose-android` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx` | 1.4.0 | Apache-2.0 |
| `androidx.startup:startup-runtime` | 1.1.1 | Apache-2.0 |
| `androidx.tracing:tracing` | 2.0.1 | Apache-2.0 |
| `androidx.tracing:tracing-android` | 2.0.1 | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | Apache-2.0 |
| `androidx.window:window` | 1.5.0 | Apache-2.0 |
| `androidx.window:window-core` | 1.5.0 | Apache-2.0 |
| `androidx.window:window-core-android` | 1.5.0 | Apache-2.0 |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 |
| `com.squareup.okio:okio` | 3.17.0 | Apache-2.0 |
| `com.squareup.okio:okio-jvm` | 3.17.0 | Apache-2.0 |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.21 | Apache-2.0 |
| `org.jetbrains.kotlinx:atomicfu` | 0.28.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:atomicfu-jvm` | 0.28.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.9.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.9.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.9.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 | Apache-2.0 |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 |
