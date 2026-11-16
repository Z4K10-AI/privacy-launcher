# Ship Privacy Launcher as the system home screen.
#
# Include from the device makefile:
#     $(call inherit-product, packages/apps/PrivacyLauncher/privacy-launcher.mk)

PRODUCT_PACKAGES += \
    PrivacyLauncher

# Android's own launcher stays in the image. Do not remove it.
#
# An earlier version of this file took it out (PRODUCT_PACKAGES_REMOVE of
# Trebuchet, Launcher3 and Launcher3QuickStep) so that there would be only
# one home app and no question on first boot. That breaks the system: since
# Android 10 the recent-apps screen and the navigation buttons are a service
# inside that package, and with the package gone there is neither. Switching
# off only its home screen does not work either: the same service then
# crashes as it starts.
#
# Nothing needs removing. The launcher's own home screen carries a priority
# of 1 in its manifest, Android honours that for a privileged system app
# (see Android.bp), and so it is chosen ahead of the stock launcher on first
# boot without asking. All three cases were run in the emulator on Android
# 15; docs/os.md has the results.
