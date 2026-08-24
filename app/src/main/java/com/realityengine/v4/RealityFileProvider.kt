package com.realityengine.v4

import androidx.core.content.FileProvider

/** Dedicated provider for handing a downloaded update APK to Android's package installer. */
class RealityFileProvider : FileProvider(R.xml.update_file_paths)
