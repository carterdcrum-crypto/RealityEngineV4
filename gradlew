#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -x "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  :
else
  echo "Gradle wrapper JAR is missing." >&2
  exit 1
fi

exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
