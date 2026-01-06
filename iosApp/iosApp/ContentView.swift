import SwiftUI
import LoyaltyLoop // <-- Это имя твоего Shared-модуля (из baseName в Gradle)

// 1. Создаем обертку: превращаем UIViewController (из Kotlin) в SwiftUI View
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // Вызываем Kotlin-функцию MainViewController(), она доступна напрямую
        return MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Здесь можно обновлять состояние, если нужно (пока оставляем пустым)
    }
}

// 2. Отображаем эту обертку
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all) // Важно! Чтобы карта и UI были на весь экран (под челкой и статус-баром)
    }
}

// Превью для Xcode (опционально, может не работать с KMP, но пусть будет)
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}