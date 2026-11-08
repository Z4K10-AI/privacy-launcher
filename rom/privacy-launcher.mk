# Ship Privacy Launcher as the system home screen.
#
# Include from the device makefile:
#     $(call inherit-product, packages/apps/PrivacyLauncher/privacy-launcher.mk)

PRODUCT_PACKAGES += \
    PrivacyLauncher

# Keep the stock launcher out of the image.
#
# Without this the build has two home apps and Android asks the user to pick
# one on first boot, which rather defeats the point of baking ours in. The
# name differs by tree: Trebuchet on LineageOS, Launcher3 or
# Launcher3QuickStep on AOSP.
PRODUCT_PACKAGES_REMOVE += \
    Trebuchet \
    Launcher3 \
    Launcher3QuickStep
