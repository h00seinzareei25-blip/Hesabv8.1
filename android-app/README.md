# دفتر حساب — نسخه اندروید (APK)

این پوشه اپ وب «دفتر حساب» را با [Capacitor](https://capacitorjs.com/) به اپ اندروید تبدیل می‌کند.

## ساخت APK

پیش‌نیاز: JDK 17+، Android SDK (platform 35، build-tools 35).

```bash
cd android-app
npm install
export ANDROID_HOME=/path/to/android-sdk
npx cap sync android
cd android && ./gradlew assembleDebug
```

خروجی دیباگ:

`android/app/build/outputs/apk/debug/app-debug.apk`

برای نصب روی گوشی:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## نکات

- این APK از نوع **debug** است (قابل نصب مستقیم، بدون انتشار در Play Store).
- داده‌ها روی دستگاه در WebView ذخیره می‌شوند (localStorage / IndexedDB).
- دوربین برای عکس رسید نیاز به مجوز دارد.
- Service Worker در محیط Capacitor غیرفعال است؛ آفلاین بودن از بسته‌بندی محلی فایل‌ها تأمین می‌شود.
