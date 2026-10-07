import WidgetKit
import SwiftUI

/// The extension's entry point.
///
/// One widget today. The bundle exists because WidgetKit requires it, and
/// because a second widget — a stats one, say — should be a line here rather
/// than a second extension.
@main
struct WatchGuruWidgetBundle: WidgetBundle {
    var body: some Widget {
        UpNextWidget()
    }
}
