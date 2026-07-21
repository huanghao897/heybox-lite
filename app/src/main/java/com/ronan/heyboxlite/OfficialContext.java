package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.ActivityInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Base64;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import java.util.List;

final class OfficialContext extends ContextWrapper {
    static final String PACKAGE_NAME = "com.max.xiaoheihe";
    private static int officialVersionCode = 1112;
    private static String officialVersionName = "1.3.391";
    private static final String OFFICIAL_CERT_DER_BASE64 =
            "MIIDczCCAlugAwIBAgIEaFwjLDANBgkqhkiG9w0BAQsFADBqMQswCQYDVQQGEwJDTjEQMA4GA1UECBMHYmVpamluZzEQMA4GA1UEBxMHYmVpamluZzERMA8GA1UEChMIcWluZ2ZlbmcxETAPBgNVBAsTCHFpbmdmZW5nMREwDwYDVQQDEwhxaW5nZmVuZzAeFw0xNTA1MDgxMTU0NThaFw00NTA0MzAxMTU0NThaMGoxCzAJBgNVBAYTAkNOMRAwDgYDVQQIEwdiZWlqaW5nMRAwDgYDVQQHEwdiZWlqaW5nMREwDwYDVQQKEwhxaW5nZmVuZzERMA8GA1UECxMIcWluZ2ZlbmcxETAPBgNVBAMTCHFpbmdmZW5nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtzzoShoY+U0LBrETsFBVDrZmbTKgtRAU/AQOtJoz/ty0ZmfEx2FC+xDFF1o5mqWiVan8+r+3j9cxqN0cn3BD8tNiq9UtLgJ/17YtJs2y4N7N0Crh9zP9/8aaqmPaTTlmPmnFYE0xwY0gJTVZMBNtQbFgYayQVQ2v1l+jYGFHpBPWPPyGX9AfdNigis7zEN2Y0aAoxQqGd+v7YiA2zd+ii2HOpmtsEUfRoF7DkGnWndVQseuxu7VHhVyFGqEr7wT8XTZbTJv+SmA6CEj73mkv+N5Kd9X3Ndgf7lhpmPdDyhEnr2rP/0kqT3A9V1+5B1a3ygOvyMtvQTZb8HReAodkEQIDAQABoyEwHzAdBgNVHQ4EFgQU7pPe6rxRG/YQxn9kWAl+IN+rImUwDQYJKoZIhvcNAQELBQADggEBAJqq+VP3di/FD6LdUh9CYoVA0XTeXthyds6AdD/8iO5Y1eRZt6E2GppAKwePSit8otzUYa2YUDYzIIqHVspu/x5g/3X/tLhGjpithD/pODJHZaSbu4wgznEukhzaU4471Bd20O2YEqEISSggLNLm6eoN504kQkCCozBf/fJ5dPbxzh8mpZ3VQ1KJhb0I5p/+ZWMDEMN1f+Zt0exERRIBIwTor73roZH75i6mInLHjIIapfuZFB0VyMN4TKgcTg33CjKJFLiuAtTLIRiC1w7BBQxyjjmrQbXkofVOOb4BF/DY3rjyQ+q4etRAW3G1vUmemTZaSomCqobULPvaaURQBGE=";
    private static final Signature[] OFFICIAL_SIGNATURES = new Signature[] {
            new Signature(Base64.decode(OFFICIAL_CERT_DER_BASE64, Base64.DEFAULT))
    };

    private final Context base;
    private PackageManager packageManager;

    OfficialContext(Context base) {
        super(base);
        this.base = base;
    }

    static void configureAppInfo(String versionName, int versionCode) {
        if (versionName != null && !versionName.trim().isEmpty()) {
            officialVersionName = versionName.trim();
        }
        if (versionCode > 0) officialVersionCode = versionCode;
    }

    static String officialVersionName() {
        return officialVersionName;
    }

    static String officialBuildCode() {
        return String.valueOf(officialVersionCode);
    }

    static Signature[] officialSignatures() {
        return OFFICIAL_SIGNATURES.clone();
    }

    @Override public Context getApplicationContext() {
        return this;
    }

    @Override public String getPackageName() {
        return PACKAGE_NAME;
    }

    @Override public PackageManager getPackageManager() {
        if (packageManager == null) {
            packageManager = new OfficialPackageManager(base.getPackageManager(),
                    base.getPackageName());
        }
        return packageManager;
    }

    @Override public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo(base.getApplicationInfo());
        info.packageName = PACKAGE_NAME;
        return info;
    }

    @Override public String getPackageCodePath() {
        return base.getPackageCodePath();
    }

    @Override public String getPackageResourcePath() {
        return base.getPackageResourcePath();
    }

    @SuppressLint("NewApi")
    private static final class OfficialPackageManager extends PackageManager {
        private final PackageManager base;
        private final String realPackage;

        OfficialPackageManager(PackageManager base, String realPackage) {
            this.base = base;
            this.realPackage = realPackage;
        }

        @Override public PackageInfo getPackageInfo(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            PackageInfo info = base.getPackageInfo(resolve(packageName), flags);
            if (PACKAGE_NAME.equals(packageName)) {
                applyOfficialPackageInfo(info);
            }
            return info;
        }

        @Override public ApplicationInfo getApplicationInfo(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            ApplicationInfo info = new ApplicationInfo(
                    base.getApplicationInfo(resolve(packageName), flags));
            if (PACKAGE_NAME.equals(packageName)) info.packageName = PACKAGE_NAME;
            return info;
        }

        @Override public CharSequence getApplicationLabel(ApplicationInfo info) {
            return base.getApplicationLabel(info);
        }

        @Override public String[] getPackagesForUid(int uid) {
            return base.getPackagesForUid(uid);
        }

        @Override public String getNameForUid(int uid) {
            return base.getNameForUid(uid);
        }

        @Override public int checkSignatures(String pkg1, String pkg2) {
            return base.checkSignatures(resolve(pkg1), resolve(pkg2));
        }

        private String resolve(String packageName) {
            return PACKAGE_NAME.equals(packageName) ? realPackage : packageName;
        }

        @SuppressWarnings("deprecation")
        private static void applyOfficialPackageInfo(PackageInfo info) {
            if (info == null) return;
            info.packageName = PACKAGE_NAME;
            info.versionCode = officialVersionCode;
            info.versionName = officialVersionName;
            info.signatures = OFFICIAL_SIGNATURES.clone();
            if (info.applicationInfo != null) {
                ApplicationInfo app = new ApplicationInfo(info.applicationInfo);
                app.packageName = PACKAGE_NAME;
                info.applicationInfo = app;
            }
        }

        @Override public void addPackageToPreferred(String packageName) {
            base.addPackageToPreferred(resolve(packageName));
        }

        @Override public boolean addPermission(PermissionInfo info) {
            return base.addPermission(info);
        }

        @Override public boolean addPermissionAsync(PermissionInfo info) {
            return base.addPermissionAsync(info);
        }

        @Override public void addPreferredActivity(IntentFilter filter, int match,
                                                   ComponentName[] set, ComponentName activity) {
            base.addPreferredActivity(filter, match, set, activity);
        }

        @Override public boolean canRequestPackageInstalls() {
            return base.canRequestPackageInstalls();
        }

        @Override public String[] canonicalToCurrentPackageNames(String[] names) {
            return base.canonicalToCurrentPackageNames(names);
        }

        @Override public int checkPermission(String permission, String packageName) {
            return base.checkPermission(permission, resolve(packageName));
        }

        @Override public int checkSignatures(int uid1, int uid2) {
            return base.checkSignatures(uid1, uid2);
        }

        @Override public void clearInstantAppCookie() {
            base.clearInstantAppCookie();
        }

        @Override public void clearPackagePreferredActivities(String packageName) {
            base.clearPackagePreferredActivities(resolve(packageName));
        }

        @Override public String[] currentToCanonicalPackageNames(String[] names) {
            return base.currentToCanonicalPackageNames(names);
        }

        @Override public void extendVerificationTimeout(int id, int codeAtTimeout,
                                                        long millisecondsToDelay) {
            base.extendVerificationTimeout(id, codeAtTimeout, millisecondsToDelay);
        }

        @Override public Drawable getActivityBanner(ComponentName activity)
                throws PackageManager.NameNotFoundException {
            return base.getActivityBanner(activity);
        }

        @Override public Drawable getActivityBanner(Intent intent)
                throws PackageManager.NameNotFoundException {
            return base.getActivityBanner(intent);
        }

        @Override public Drawable getActivityIcon(ComponentName activity)
                throws PackageManager.NameNotFoundException {
            return base.getActivityIcon(activity);
        }

        @Override public Drawable getActivityIcon(Intent intent)
                throws PackageManager.NameNotFoundException {
            return base.getActivityIcon(intent);
        }

        @Override public ActivityInfo getActivityInfo(ComponentName activity, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getActivityInfo(activity, flags);
        }

        @Override public Drawable getActivityLogo(ComponentName activity)
                throws PackageManager.NameNotFoundException {
            return base.getActivityLogo(activity);
        }

        @Override public Drawable getActivityLogo(Intent intent)
                throws PackageManager.NameNotFoundException {
            return base.getActivityLogo(intent);
        }

        @Override public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
            return base.getAllPermissionGroups(flags);
        }

        @Override public Drawable getApplicationBanner(ApplicationInfo info) {
            return base.getApplicationBanner(info);
        }

        @Override public Drawable getApplicationBanner(String packageName)
                throws PackageManager.NameNotFoundException {
            return base.getApplicationBanner(resolve(packageName));
        }

        @Override public int getApplicationEnabledSetting(String packageName) {
            return base.getApplicationEnabledSetting(resolve(packageName));
        }

        @Override public Drawable getApplicationIcon(ApplicationInfo info) {
            return base.getApplicationIcon(info);
        }

        @Override public Drawable getApplicationIcon(String packageName)
                throws PackageManager.NameNotFoundException {
            return base.getApplicationIcon(resolve(packageName));
        }

        @Override public Drawable getApplicationLogo(ApplicationInfo info) {
            return base.getApplicationLogo(info);
        }

        @Override public Drawable getApplicationLogo(String packageName)
                throws PackageManager.NameNotFoundException {
            return base.getApplicationLogo(resolve(packageName));
        }

        @Override public ChangedPackages getChangedPackages(int sequenceNumber) {
            return base.getChangedPackages(sequenceNumber);
        }

        @Override public int getComponentEnabledSetting(ComponentName componentName) {
            return base.getComponentEnabledSetting(componentName);
        }

        @Override public Drawable getDefaultActivityIcon() {
            return base.getDefaultActivityIcon();
        }

        @Override public Drawable getDrawable(String packageName, int resId,
                                              ApplicationInfo appInfo) {
            return base.getDrawable(resolve(packageName), resId, appInfo);
        }

        @Override public List<ApplicationInfo> getInstalledApplications(int flags) {
            return base.getInstalledApplications(flags);
        }

        @Override public List<PackageInfo> getInstalledPackages(int flags) {
            return base.getInstalledPackages(flags);
        }

        @Override public String getInstallerPackageName(String packageName) {
            return base.getInstallerPackageName(resolve(packageName));
        }

        @Override public byte[] getInstantAppCookie() {
            return base.getInstantAppCookie();
        }

        @Override public int getInstantAppCookieMaxBytes() {
            return base.getInstantAppCookieMaxBytes();
        }

        @Override public InstrumentationInfo getInstrumentationInfo(ComponentName className,
                                                                    int flags)
                throws PackageManager.NameNotFoundException {
            return base.getInstrumentationInfo(className, flags);
        }

        @Override public Intent getLaunchIntentForPackage(String packageName) {
            return base.getLaunchIntentForPackage(resolve(packageName));
        }

        @Override public Intent getLeanbackLaunchIntentForPackage(String packageName) {
            return base.getLeanbackLaunchIntentForPackage(resolve(packageName));
        }

        @Override public int[] getPackageGids(String packageName)
                throws PackageManager.NameNotFoundException {
            return base.getPackageGids(resolve(packageName));
        }

        @Override public int[] getPackageGids(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getPackageGids(resolve(packageName), flags);
        }

        @Override public PackageInfo getPackageInfo(android.content.pm.VersionedPackage versionedPackage,
                                                    int flags)
                throws PackageManager.NameNotFoundException {
            return base.getPackageInfo(versionedPackage, flags);
        }

        @Override public PackageInstaller getPackageInstaller() {
            return base.getPackageInstaller();
        }

        @Override public int getPackageUid(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getPackageUid(resolve(packageName), flags);
        }

        @Override public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions,
                                                                         int flags) {
            return base.getPackagesHoldingPermissions(permissions, flags);
        }

        @Override public PermissionGroupInfo getPermissionGroupInfo(String name, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getPermissionGroupInfo(name, flags);
        }

        @Override public PermissionInfo getPermissionInfo(String name, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getPermissionInfo(name, flags);
        }

        @Override public int getPreferredActivities(List<IntentFilter> outFilters,
                                                    List<ComponentName> outActivities,
                                                    String packageName) {
            return base.getPreferredActivities(outFilters, outActivities, resolve(packageName));
        }

        @Override public List<PackageInfo> getPreferredPackages(int flags) {
            return base.getPreferredPackages(flags);
        }

        @Override public ProviderInfo getProviderInfo(ComponentName component, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getProviderInfo(component, flags);
        }

        @Override public ActivityInfo getReceiverInfo(ComponentName component, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getReceiverInfo(component, flags);
        }

        @Override public Resources getResourcesForActivity(ComponentName activity)
                throws PackageManager.NameNotFoundException {
            return base.getResourcesForActivity(activity);
        }

        @Override public Resources getResourcesForApplication(ApplicationInfo app)
                throws PackageManager.NameNotFoundException {
            return base.getResourcesForApplication(app);
        }

        @Override public Resources getResourcesForApplication(String packageName)
                throws PackageManager.NameNotFoundException {
            return base.getResourcesForApplication(resolve(packageName));
        }

        @Override public ServiceInfo getServiceInfo(ComponentName component, int flags)
                throws PackageManager.NameNotFoundException {
            return base.getServiceInfo(component, flags);
        }

        @Override public List<SharedLibraryInfo> getSharedLibraries(int flags) {
            return base.getSharedLibraries(flags);
        }

        @Override public FeatureInfo[] getSystemAvailableFeatures() {
            return base.getSystemAvailableFeatures();
        }

        @Override public String[] getSystemSharedLibraryNames() {
            return base.getSystemSharedLibraryNames();
        }

        @Override public CharSequence getText(String packageName, int resId,
                                              ApplicationInfo appInfo) {
            return base.getText(resolve(packageName), resId, appInfo);
        }

        @Override public Drawable getUserBadgedDrawableForDensity(Drawable drawable,
                                                                  UserHandle user,
                                                                  Rect badgeLocation,
                                                                  int badgeDensity) {
            return base.getUserBadgedDrawableForDensity(drawable, user, badgeLocation,
                    badgeDensity);
        }

        @Override public Drawable getUserBadgedIcon(Drawable icon, UserHandle user) {
            return base.getUserBadgedIcon(icon, user);
        }

        @Override public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
            return base.getUserBadgedLabel(label, user);
        }

        @Override public XmlResourceParser getXml(String packageName, int resId,
                                                  ApplicationInfo appInfo) {
            return base.getXml(resolve(packageName), resId, appInfo);
        }

        @Override public boolean hasSystemFeature(String name) {
            return base.hasSystemFeature(name);
        }

        @Override public boolean hasSystemFeature(String name, int version) {
            return base.hasSystemFeature(name, version);
        }

        @Override public boolean isInstantApp() {
            return base.isInstantApp();
        }

        @Override public boolean isInstantApp(String packageName) {
            return base.isInstantApp(resolve(packageName));
        }

        @Override public boolean isPermissionRevokedByPolicy(String permission,
                                                             String packageName) {
            return base.isPermissionRevokedByPolicy(permission, resolve(packageName));
        }

        @Override public boolean isSafeMode() {
            return base.isSafeMode();
        }

        @Override public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
            return base.queryBroadcastReceivers(intent, flags);
        }

        @Override public List<ProviderInfo> queryContentProviders(String processName,
                                                                  int uid, int flags) {
            return base.queryContentProviders(processName, uid, flags);
        }

        @Override public List<InstrumentationInfo> queryInstrumentation(String targetPackage,
                                                                        int flags) {
            return base.queryInstrumentation(resolve(targetPackage), flags);
        }

        @Override public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
            return base.queryIntentActivities(intent, flags);
        }

        @Override public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller,
                                                                      Intent[] specifics,
                                                                      Intent intent,
                                                                      int flags) {
            return base.queryIntentActivityOptions(caller, specifics, intent, flags);
        }

        @Override public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
            return base.queryIntentContentProviders(intent, flags);
        }

        @Override public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
            return base.queryIntentServices(intent, flags);
        }

        @Override public List<PermissionInfo> queryPermissionsByGroup(String group, int flags)
                throws PackageManager.NameNotFoundException {
            return base.queryPermissionsByGroup(group, flags);
        }

        @Override public void removePackageFromPreferred(String packageName) {
            base.removePackageFromPreferred(resolve(packageName));
        }

        @Override public void removePermission(String name) {
            base.removePermission(name);
        }

        @Override public ResolveInfo resolveActivity(Intent intent, int flags) {
            return base.resolveActivity(intent, flags);
        }

        @Override public ProviderInfo resolveContentProvider(String name, int flags) {
            return base.resolveContentProvider(name, flags);
        }

        @Override public ResolveInfo resolveService(Intent intent, int flags) {
            return base.resolveService(intent, flags);
        }

        @Override public void setApplicationCategoryHint(String packageName, int categoryHint) {
            base.setApplicationCategoryHint(resolve(packageName), categoryHint);
        }

        @Override public void setApplicationEnabledSetting(String packageName, int newState,
                                                           int flags) {
            base.setApplicationEnabledSetting(resolve(packageName), newState, flags);
        }

        @Override public void setComponentEnabledSetting(ComponentName componentName,
                                                         int newState, int flags) {
            base.setComponentEnabledSetting(componentName, newState, flags);
        }

        @Override public void setInstallerPackageName(String targetPackage,
                                                      String installerPackageName) {
            base.setInstallerPackageName(resolve(targetPackage), installerPackageName);
        }

        @Override public void updateInstantAppCookie(byte[] cookie) {
            base.updateInstantAppCookie(cookie);
        }

        @Override public void verifyPendingInstall(int id, int verificationCode) {
            base.verifyPendingInstall(id, verificationCode);
        }
    }
}
