# LinePhoneDemo
linephone集成示例

Sdk集成步骤 复制 linphone-sdk-android-4.2-39-g6b4efc8.aar 到app/libs目录

app/build.gradle 中添加

allprojects {
    repositories {
        jcenter()
        flatDir {
            dirs 'libs'
        }
    }
}
 implementation(name: 'linphone-sdk-android-4.2-39-g6b4efc8', ext: 'aar')
