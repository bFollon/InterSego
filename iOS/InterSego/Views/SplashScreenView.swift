/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import SwiftUI

struct SplashScreenView: View {
    private let iconGreen = Color(red: 0.376, green: 0.639, blue: 0.498)

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            Image("SplashIcon")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 120, height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 27))

            Spacer().frame(height: 24)

            Text("InterSego")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(iconGreen)

            Text("Interurbanos de Segovia")
                .font(.system(size: 18, weight: .regular))
                .foregroundStyle(.secondary)
                .padding(.top, 4)

            Spacer()

            ProgressView()
                .controlSize(.regular)
                .padding(.bottom, 60)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}
