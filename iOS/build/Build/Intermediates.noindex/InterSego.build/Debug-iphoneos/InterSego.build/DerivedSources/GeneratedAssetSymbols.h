#import <Foundation/Foundation.h>

#if __has_attribute(swift_private)
#define AC_SWIFT_PRIVATE __attribute__((swift_private))
#else
#define AC_SWIFT_PRIVATE
#endif

/// The resource bundle ID.
static NSString * const ACBundleID AC_SWIFT_PRIVATE = @"com.github.bfollon.intersego";

/// The "AccentColor" asset catalog color resource.
static NSString * const ACColorNameAccentColor AC_SWIFT_PRIVATE = @"AccentColor";

/// The "SplashIcon" asset catalog image resource.
static NSString * const ACImageNameSplashIcon AC_SWIFT_PRIVATE = @"SplashIcon";

/// The "tutorial_bell_daily" asset catalog image resource.
static NSString * const ACImageNameTutorialBellDaily AC_SWIFT_PRIVATE = @"tutorial_bell_daily";

/// The "tutorial_bell_intro" asset catalog image resource.
static NSString * const ACImageNameTutorialBellIntro AC_SWIFT_PRIVATE = @"tutorial_bell_intro";

/// The "tutorial_bell_oneoff" asset catalog image resource.
static NSString * const ACImageNameTutorialBellOneoff AC_SWIFT_PRIVATE = @"tutorial_bell_oneoff";

/// The "tutorial_live_boarded" asset catalog image resource.
static NSString * const ACImageNameTutorialLiveBoarded AC_SWIFT_PRIVATE = @"tutorial_live_boarded";

/// The "tutorial_live_boarding_button" asset catalog image resource.
static NSString * const ACImageNameTutorialLiveBoardingButton AC_SWIFT_PRIVATE = @"tutorial_live_boarding_button";

/// The "tutorial_reminders_screen" asset catalog image resource.
static NSString * const ACImageNameTutorialRemindersScreen AC_SWIFT_PRIVATE = @"tutorial_reminders_screen";

#undef AC_SWIFT_PRIVATE
