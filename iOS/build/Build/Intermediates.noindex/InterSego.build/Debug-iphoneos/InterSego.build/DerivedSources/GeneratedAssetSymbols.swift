import Foundation
#if canImport(AppKit)
import AppKit
#endif
#if canImport(UIKit)
import UIKit
#endif
#if canImport(SwiftUI)
import SwiftUI
#endif
#if canImport(DeveloperToolsSupport)
import DeveloperToolsSupport
#endif

#if SWIFT_PACKAGE
private let resourceBundle = Foundation.Bundle.module
#else
private class ResourceBundleClass {}
private let resourceBundle = Foundation.Bundle(for: ResourceBundleClass.self)
#endif

// MARK: - Color Symbols -

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension DeveloperToolsSupport.ColorResource {

    /// The "AccentColor" asset catalog color resource.
    static let accent = DeveloperToolsSupport.ColorResource(name: "AccentColor", bundle: resourceBundle)

}

// MARK: - Image Symbols -

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension DeveloperToolsSupport.ImageResource {

    /// The "SplashIcon" asset catalog image resource.
    static let splashIcon = DeveloperToolsSupport.ImageResource(name: "SplashIcon", bundle: resourceBundle)

    /// The "tutorial_bell_daily" asset catalog image resource.
    static let tutorialBellDaily = DeveloperToolsSupport.ImageResource(name: "tutorial_bell_daily", bundle: resourceBundle)

    /// The "tutorial_bell_intro" asset catalog image resource.
    static let tutorialBellIntro = DeveloperToolsSupport.ImageResource(name: "tutorial_bell_intro", bundle: resourceBundle)

    /// The "tutorial_bell_oneoff" asset catalog image resource.
    static let tutorialBellOneoff = DeveloperToolsSupport.ImageResource(name: "tutorial_bell_oneoff", bundle: resourceBundle)

    /// The "tutorial_live_boarded" asset catalog image resource.
    static let tutorialLiveBoarded = DeveloperToolsSupport.ImageResource(name: "tutorial_live_boarded", bundle: resourceBundle)

    /// The "tutorial_live_boarding_button" asset catalog image resource.
    static let tutorialLiveBoardingButton = DeveloperToolsSupport.ImageResource(name: "tutorial_live_boarding_button", bundle: resourceBundle)

    /// The "tutorial_reminders_screen" asset catalog image resource.
    static let tutorialRemindersScreen = DeveloperToolsSupport.ImageResource(name: "tutorial_reminders_screen", bundle: resourceBundle)

}

// MARK: - Color Symbol Extensions -

#if canImport(AppKit)
@available(macOS 14.0, *)
@available(macCatalyst, unavailable)
extension AppKit.NSColor {

    /// The "AccentColor" asset catalog color.
    static var accent: AppKit.NSColor {
#if !targetEnvironment(macCatalyst)
        .init(resource: .accent)
#else
        .init()
#endif
    }

}
#endif

#if canImport(UIKit)
@available(iOS 17.0, tvOS 17.0, *)
@available(watchOS, unavailable)
extension UIKit.UIColor {

    /// The "AccentColor" asset catalog color.
    static var accent: UIKit.UIColor {
#if !os(watchOS)
        .init(resource: .accent)
#else
        .init()
#endif
    }

}
#endif

#if canImport(SwiftUI)
@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension SwiftUI.Color {

    /// The "AccentColor" asset catalog color.
    static var accent: SwiftUI.Color { .init(.accent) }

}

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension SwiftUI.ShapeStyle where Self == SwiftUI.Color {

    /// The "AccentColor" asset catalog color.
    static var accent: SwiftUI.Color { .init(.accent) }

}
#endif

// MARK: - Image Symbol Extensions -

#if canImport(AppKit)
@available(macOS 14.0, *)
@available(macCatalyst, unavailable)
extension AppKit.NSImage {

    /// The "SplashIcon" asset catalog image.
    static var splashIcon: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .splashIcon)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_daily" asset catalog image.
    static var tutorialBellDaily: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialBellDaily)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_intro" asset catalog image.
    static var tutorialBellIntro: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialBellIntro)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_oneoff" asset catalog image.
    static var tutorialBellOneoff: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialBellOneoff)
#else
        .init()
#endif
    }

    /// The "tutorial_live_boarded" asset catalog image.
    static var tutorialLiveBoarded: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialLiveBoarded)
#else
        .init()
#endif
    }

    /// The "tutorial_live_boarding_button" asset catalog image.
    static var tutorialLiveBoardingButton: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialLiveBoardingButton)
#else
        .init()
#endif
    }

    /// The "tutorial_reminders_screen" asset catalog image.
    static var tutorialRemindersScreen: AppKit.NSImage {
#if !targetEnvironment(macCatalyst)
        .init(resource: .tutorialRemindersScreen)
#else
        .init()
#endif
    }

}
#endif

#if canImport(UIKit)
@available(iOS 17.0, tvOS 17.0, *)
@available(watchOS, unavailable)
extension UIKit.UIImage {

    /// The "SplashIcon" asset catalog image.
    static var splashIcon: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .splashIcon)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_daily" asset catalog image.
    static var tutorialBellDaily: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialBellDaily)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_intro" asset catalog image.
    static var tutorialBellIntro: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialBellIntro)
#else
        .init()
#endif
    }

    /// The "tutorial_bell_oneoff" asset catalog image.
    static var tutorialBellOneoff: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialBellOneoff)
#else
        .init()
#endif
    }

    /// The "tutorial_live_boarded" asset catalog image.
    static var tutorialLiveBoarded: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialLiveBoarded)
#else
        .init()
#endif
    }

    /// The "tutorial_live_boarding_button" asset catalog image.
    static var tutorialLiveBoardingButton: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialLiveBoardingButton)
#else
        .init()
#endif
    }

    /// The "tutorial_reminders_screen" asset catalog image.
    static var tutorialRemindersScreen: UIKit.UIImage {
#if !os(watchOS)
        .init(resource: .tutorialRemindersScreen)
#else
        .init()
#endif
    }

}
#endif

// MARK: - Thinnable Asset Support -

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
@available(watchOS, unavailable)
extension DeveloperToolsSupport.ColorResource {

    private init?(thinnableName: Swift.String, bundle: Foundation.Bundle) {
#if canImport(AppKit) && os(macOS)
        if AppKit.NSColor(named: NSColor.Name(thinnableName), bundle: bundle) != nil {
            self.init(name: thinnableName, bundle: bundle)
        } else {
            return nil
        }
#elseif canImport(UIKit) && !os(watchOS)
        if UIKit.UIColor(named: thinnableName, in: bundle, compatibleWith: nil) != nil {
            self.init(name: thinnableName, bundle: bundle)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}

#if canImport(AppKit)
@available(macOS 14.0, *)
@available(macCatalyst, unavailable)
extension AppKit.NSColor {

    private convenience init?(thinnableResource: DeveloperToolsSupport.ColorResource?) {
#if !targetEnvironment(macCatalyst)
        if let resource = thinnableResource {
            self.init(resource: resource)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}
#endif

#if canImport(UIKit)
@available(iOS 17.0, tvOS 17.0, *)
@available(watchOS, unavailable)
extension UIKit.UIColor {

    private convenience init?(thinnableResource: DeveloperToolsSupport.ColorResource?) {
#if !os(watchOS)
        if let resource = thinnableResource {
            self.init(resource: resource)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}
#endif

#if canImport(SwiftUI)
@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension SwiftUI.Color {

    private init?(thinnableResource: DeveloperToolsSupport.ColorResource?) {
        if let resource = thinnableResource {
            self.init(resource)
        } else {
            return nil
        }
    }

}

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
extension SwiftUI.ShapeStyle where Self == SwiftUI.Color {

    private init?(thinnableResource: DeveloperToolsSupport.ColorResource?) {
        if let resource = thinnableResource {
            self.init(resource)
        } else {
            return nil
        }
    }

}
#endif

@available(iOS 17.0, macOS 14.0, tvOS 17.0, watchOS 10.0, *)
@available(watchOS, unavailable)
extension DeveloperToolsSupport.ImageResource {

    private init?(thinnableName: Swift.String, bundle: Foundation.Bundle) {
#if canImport(AppKit) && os(macOS)
        if bundle.image(forResource: NSImage.Name(thinnableName)) != nil {
            self.init(name: thinnableName, bundle: bundle)
        } else {
            return nil
        }
#elseif canImport(UIKit) && !os(watchOS)
        if UIKit.UIImage(named: thinnableName, in: bundle, compatibleWith: nil) != nil {
            self.init(name: thinnableName, bundle: bundle)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}

#if canImport(AppKit)
@available(macOS 14.0, *)
@available(macCatalyst, unavailable)
extension AppKit.NSImage {

    private convenience init?(thinnableResource: DeveloperToolsSupport.ImageResource?) {
#if !targetEnvironment(macCatalyst)
        if let resource = thinnableResource {
            self.init(resource: resource)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}
#endif

#if canImport(UIKit)
@available(iOS 17.0, tvOS 17.0, *)
@available(watchOS, unavailable)
extension UIKit.UIImage {

    private convenience init?(thinnableResource: DeveloperToolsSupport.ImageResource?) {
#if !os(watchOS)
        if let resource = thinnableResource {
            self.init(resource: resource)
        } else {
            return nil
        }
#else
        return nil
#endif
    }

}
#endif

