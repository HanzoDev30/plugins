import Foundation

/// Sample Swift target, so the `.swift` routing of the provider gets exercised too.
struct Greeter {
    let name: String
    var punctuation: String = "!"

    /// Greeting for this greeter, e.g. `Hello, Ghost!`.
    var greeting: String {
        "Hello, \(name)\(punctuation)"
    }

    /// Repeats the greeting; the default keeps the call sites short.
    func greet(times: Int = 1) -> String {
        var out = ""
        for _ in 0..<max(times, 1) {
            out += greeting + "\n"
        }
        return out
    }

    init(name: String) {
        self.name = name
    }
}

enum Language: String {
    case english = "en"
    case persian = "fa"
}

protocol Greetable {
    func greet(language: Language) -> String
}

extension Greeter: Greetable {
    func greet(language: Language) -> String {
        switch language {
        case .english: return greet()
        case .persian: return "سلام \(name)"
        }
    }
}

typealias Greeting = String


print("Hello")
let greeter = Greeter(name: "Ghost")
print(greeter.greet(times: 2).trimmingCharacters(in: .whitespacesAndNewlines))
