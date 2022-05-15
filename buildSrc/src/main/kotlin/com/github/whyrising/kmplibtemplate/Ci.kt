package com.github.whyrising.kmplibtemplate

import java.lang.System.getenv

object Ci {
  // TODO: 5/14/22 change group
  val group = "com.github.whyrising.kmplibtemplate"

  private const val snapshotBase = "0.0.1"

  private val snapshotVersion = when (val n = getenv("GITHUB_RUN_NUMBER")) {
    null -> "$snapshotBase-LOCAL"
    else -> "$snapshotBase.$n-SNAPSHOT"
  }

  private val releaseVersion get() = getenv("RELEASE_VERSION")

  val isRelease get() = releaseVersion != null

  val publishVersion = releaseVersion ?: snapshotVersion
}
