package dev.l1avzh.haven

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Whether Haven is the Home app Android will use, and whether the device offers a supported way to change that. */
enum class HomeStatus { HELD, NOT_HELD, UNSUPPORTED }

/** The decision, kept free of Android classes so it can be unit tested. */
object HomeRoleLogic {
    /**
     * [roleHeld] is the Home role's answer on API 29+ (null where the role does not exist), [resolvedPackage] the package the system picks for a HOME
     * intent. The role is authoritative when it exists; otherwise the resolved handler is. Opening settings is never treated as having chosen Haven.
     */
    fun status(roleHeld: Boolean?, resolvedPackage: String?, ownPackage: String, canOpenPicker: Boolean): HomeStatus {
        val held = roleHeld ?: (resolvedPackage == ownPackage)
        return when { held -> HomeStatus.HELD; canOpenPicker -> HomeStatus.NOT_HELD; else -> HomeStatus.UNSUPPORTED }
    }
}

/**
 * The supported ways to become the default Home app, and nothing else: on API 29+ the system's own consent dialog for the Home role
 * ([RoleManager.createRequestRoleIntent]); before that, or if the role is missing, the system Home/default-apps settings screens. Haven never grants the
 * role itself and never forces itself to the front. This tells you what Android recorded, not what a TV's firmware does with its Home button.
 */
object HomeRole {
    private val HOME = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** True/false from the Home role on API 29+ when the role exists; null when it cannot be asked. */
    private fun roleHeld(ctx: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        return try { if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.isRoleHeld(RoleManager.ROLE_HOME) else null } catch (_: Exception) { null }
    }

    /** The package a HOME intent resolves to right now (a resolver/"android" package when the user has not picked one). */
    private fun resolved(ctx: Context): String? = try {
        ctx.packageManager.resolveActivity(HOME, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (_: Exception) { null }

    /** The consent dialog for the Home role, or null when the role is unavailable or already held. */
    fun roleRequest(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        return try { if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else null } catch (_: Exception) { null }
    }

    /** System settings screens that let the user pick a Home app, best first, only those this device can actually open. */
    fun settingsScreens(ctx: Context): List<Intent> = listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .map { Intent(it) }.filter { it.resolveActivity(ctx.packageManager) != null }

    fun status(ctx: Context): HomeStatus {
        val picker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleRequest(ctx) != null || settingsScreens(ctx).isNotEmpty()
        return HomeRoleLogic.status(roleHeld(ctx), resolved(ctx), ctx.packageName, picker)
    }
}
