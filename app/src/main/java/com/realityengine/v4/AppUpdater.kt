package com.realityengine.v4

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Downloads the latest green APK published by CI and hands it to Android's package installer. */
class AppUpdater(private val context:Context){
 data class UpdateInfo(val versionCode:Int,val versionName:String,val buildId:String,val apkUrl:String)
 sealed class CheckResult{data class Available(val info:UpdateInfo):CheckResult();data class Current(val versionName:String):CheckResult();data class Failed(val reason:String):CheckResult()}
 private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
 fun check(callback:(CheckResult)->Unit){Thread{val result=try{val body=client.newCall(Request.Builder().url(METADATA_URL).header("Cache-Control","no-cache").build()).execute().use{r->if(!r.isSuccessful)throw IllegalStateException("Update server returned ${r.code}");r.body?.string()?:throw IllegalStateException("Empty update response")};val json=JSONObject(body);val info=UpdateInfo(json.getInt("versionCode"),json.getString("versionName"),json.optString("buildId","latest"),APK_URL);if(info.versionCode>BuildConfig.VERSION_CODE)CheckResult.Available(info)else CheckResult.Current(BuildConfig.VERSION_NAME)}catch(t:Throwable){CheckResult.Failed(t.message?:"Update check failed")};callback(result)}.start()}
 fun downloadAndInstall(info:UpdateInfo,callback:(String)->Unit){Thread{try{val dir=File(context.cacheDir,"updates").apply{mkdirs()};val apk=File(dir,"RealityEngine-${info.versionCode}.apk");client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use{r->if(!r.isSuccessful)throw IllegalStateException("Download failed (${r.code})");val body=r.body?:throw IllegalStateException("Empty APK download");apk.outputStream().use{out->body.byteStream().use{input->input.copyTo(out)}}};val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",apk);val install=Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)};context.startActivity(install);callback("Installer opened") }catch(t:Throwable){callback(t.message?:"Update install failed")}}.start()}
 fun canInstallPackages():Boolean=context.packageManager.canRequestPackageInstalls()
 fun openInstallPermission(){context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
 companion object{private const val METADATA_URL="https://github.com/carterdcrum-crypto/RealityEngineV4/releases/download/updater-latest/update.json";private const val APK_URL="https://github.com/carterdcrum-crypto/RealityEngineV4/releases/download/updater-latest/RealityEngineV4-latest.apk"}
}
