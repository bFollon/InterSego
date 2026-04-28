/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Canonical stop definitions for all bus routes in the InterSego network.
///
/// Every physical bus stop is defined exactly once here with a route-agnostic ID.
/// Parsers reference these definitions instead of declaring their own, so that
/// cross-route stop identity is automatic: two routes that reference the same
/// `BusStopRegistry` entry share the same `stopId` in their timetable records.
enum BusStopRegistry {

    // MARK: - Segovia Capital

    static let estacionAutobuses = BusStop(
        id: "estacion-autobuses",
        name: "Estación de Autobuses",
        area: "Segovia capital",
        coordinates: "40.944768, -4.121823"
    )

    static let iglesiaStTomas = BusStop(
        id: "iglesia-sto-tomas",
        name: "Iglesia Santo Tomás",
        area: "Segovia capital",
        coordinates: "40.941935, -4.118070"
    )

    static let frenteBarNorte = BusStop(
        id: "frente-bar-norte",
        name: "Frente Bar Norte",
        area: "Segovia capital",
        coordinates: "40.937206, -4.113999"
    )

    static let plazaDeToros = BusStop(
        id: "plaza-de-toros",
        name: "Plaza de Toros",
        area: "Segovia capital",
        coordinates: "40.942093, -4.107603"
    )

    /// M7 outbound only
    static let hospitalSegovia = BusStop(
        id: "hospital-segovia",
        name: "Hospital",
        area: "Segovia capital",
        coordinates: "40.944055, -4.127405"
    )

    /// M6 (inbound), M7 (inbound)
    static let jardinillos = BusStop(
        id: "jardinillos",
        name: "Jardinillos de San Roque",
        area: "Segovia capital",
        coordinates: "40.944361, -4.120831"
    )

    /// M6, M7 – Segovia side of the route toward Torrecaballeros
    static let andresLaguna = BusStop(
        id: "andres-laguna",
        name: "Andrés Laguna",
        area: "Segovia capital",
        coordinates: "40.939106, -4.115582"
    )

    /// M6, M7 – Segovia side of the route toward Torrecaballeros
    static let laPista = BusStop(
        id: "la-pista",
        name: "Glorieta La Pista",
        area: "Segovia capital",
        details: "Glorieta del Ballenoil",
        coordinates: "40.937354, -4.111411"
    )

    /// M6 only
    static let hermanitas = BusStop(
        id: "hermanitas",
        name: "Residencia Hermanitas de los pobres",
        area: "Segovia capital",
        coordinates: "40.944234, -4.110012"
    )

    /// M4, M5, M6
    static let azoguejo = BusStop(
        id: "azoguejo",
        name: "Azoguejo",
        area: "Segovia capital",
        coordinates: "40.948502, -4.115979",
        alternates: [
            AlternateLocation(
                id: "estacion-autobuses",
                name: "Estación de Autobuses",
                coordinates: "40.944768, -4.121823"
            )
        ]
    )
    
    /// M7 weekday circular — return arrival at Estación; distinct ID prevents departure merge with outbound position
    static let estacionAutobusesCircRet = BusStop(
        id: "estacion-autobuses-circ-ret",
        name: "Estación de Autobuses",
        area: "Segovia capital",
        coordinates: "40.944768, -4.121823"
    )

    /// M4
    static let azoguejoEndStop = BusStop(
        id: "azoguejo-endStop",
        name: "Azoguejo",
        area: "Segovia capital",
        coordinates: "40.948502, -4.115979"
    )

    /// M4, M6
    static let delicias = BusStop(
        id: "delicias",
        name: "Delicias",
        area: "Segovia capital",
        coordinates: "40.954500, -4.108889"
    )

    /// M6 only
    static let montecorredores = BusStop(
        id: "montecorredores",
        name: "Montecorredores",
        area: "Segovia capital",
        coordinates: "40.952000, -4.097278"
    )

    // MARK: - M1 / M2 West Area (Valverde – Garcillán – Valseca)

    /// M1, M2
    static let casinoUnion = BusStop(
        id: "casino-union",
        name: "Casino",
        area: "Casino de la Unión",
        coordinates: "40.965154, -4.209251"
    )

    /// M1 only
    static let poligonoIndM1 = BusStop(
        id: "poligono-ind-m1",
        name: "Polígono Industrial",
        area: "Valverde del Majano",
        coordinates: "40.957976, -4.198156"
    )

    /// M1 only
    static let poligonoIndM1B = BusStop(
        id: "poligono-ind-m1-2",
        name: "Polígono Industrial 2",
        area: "Valverde del Majano",
        coordinates: "40.957554, -4.206457"
    )

    /// M1 only
    static let valverdeMajano = BusStop(
        id: "valverde-majano",
        name: "Valverde de Majano",
        area: "Valverde del Majano",
        coordinates: "40.956274, -4.235343"
    )

    /// M1 only
    static let abades = BusStop(
        id: "abades",
        name: "Abades",
        area: "Abades",
        coordinates: "40.915804, -4.267038"
    )

    /// M1 only
    static let martinMiguel = BusStop(
        id: "martin-miguel",
        name: "Martín Miguel",
        area: "Martín Miguel",
        coordinates: "40.951889, -4.268660"
    )

    /// M1 only
    static let garcillan = BusStop(
        id: "garcillan",
        name: "Garcillán",
        area: "Garcillán",
        coordinates: "40.976809, -4.264724",
        alternates: [
            AlternateLocation(
                id: "garcillan-gasolinera",
                name: "Garcillán (gasolinera)",
                coordinates: "40.9745, -4.2638"
            )
        ]
    )

    /// M1 circularB return leg — distinct ID prevents departure merge with outbound position
    static let poligonoIndM1Ret = BusStop(
        id: "poligono-ind-m1-ret",
        name: "Polígono Industrial",
        area: "Valverde del Majano",
        coordinates: "40.957976, -4.198156"
    )

    /// M1 circularB return leg — distinct ID prevents departure merge with outbound position
    static let poligonoIndM1BRet = BusStop(
        id: "poligono-ind-m1-2-ret",
        name: "Polígono Industrial 2",
        area: "Valverde del Majano",
        coordinates: "40.957554, -4.206457"
    )

    /// M2 only
    static let hontanares = BusStop(
        id: "hontanares",
        name: "Hontanares de Eresma",
        area: "Hontanares de Eresma",
        coordinates: "40.983628, -4.204160"
    )

    /// M2 only
    static let losHuertos = BusStop(
        id: "los-huertos",
        name: "Los Huertos",
        area: "Los Huertos",
        coordinates: "41.009124, -4.219216"
    )

    /// M2 only
    static let valseca = BusStop(
        id: "valseca",
        name: "Valseca",
        area: "Valseca",
        coordinates: "40.999306, -4.174266"
    )

    // MARK: - M3 / M8 South (La Granja / Navacerrada / Valsaín)

    /// M3, M8
    static let urbCarrascalejo = BusStop(
        id: "urb-carrascalejo",
        name: "Urb. Carrascalejo",
        area: "Carrascalejo",
        coordinates: "40.922847, -4.078270"
    )

    /// M3, M8
    static let parqueRobledo = BusStop(
        id: "parque-robledo",
        name: "Parque Robledo",
        area: "Robledo",
        coordinates: "40.910111, -4.058750"
    )

    /// M3 only
    static let laGranja = BusStop(
        id: "la-granja",
        name: "La Granja (Pta de Segovia)",
        area: "La Granja",
        coordinates: "40.901389, -4.003333"
    )

    /// M3 only
    static let valsainPradera = BusStop(
        id: "valsain-pradera",
        name: "Valsain (La Pradera)",
        area: "Valsaín",
        coordinates: "40.877500, -4.019444"
    )

    /// M3 only
    static let bocaDelAsno = BusStop(
        id: "boca-del-asno",
        name: "Boca del Asno",
        area: "Valsaín",
        coordinates: "40.844128, -4.025668"
    )

    /// M3 only
    static let puenteMosquitos = BusStop(
        id: "puente-mosquitos",
        name: "Puente de los Mosquitos",
        area: "Navacerrada",
        coordinates: "40.823325, -4.017340"
    )

    /// M3 only
    static let navacerrada = BusStop(
        id: "navacerrada",
        name: "Navacerrada",
        area: "Navacerrada",
        coordinates: "40.780833, -4.008056"
    )

    // MARK: - M4 (La Lastrilla / El Sotillo)

    static let gasolineraLastrilla = BusStop(
        id: "gasolinera-lastrilla",
        name: "Gasolinera",
        area: "La Lastrilla",
        coordinates: "40.965944, -4.106072",
        routingCoordinates: "40.965897, -4.106276"
    )

    static let pension = BusStop(
        id: "pension",
        name: "Pensión",
        area: "La Lastrilla",
        coordinates: "40.969289, -4.107552"
    )

    static let poligonoLastrilla = BusStop(
        id: "poligono-lastrilla",
        name: "Polígono",
        area: "La Lastrilla",
        coordinates: "40.972010, -4.108356"
    )

    static let ctraValladolid = BusStop(
        id: "ctra-valladolid",
        name: "Carretera de Valladolid",
        area: "La Lastrilla",
        coordinates: "40.970464, -4.104010"
    )

    static let leopoldoMoreno = BusStop(
        id: "leopoldo-moreno",
        name: "Leopoldo Moreno",
        area: "La Lastrilla",
        coordinates: "40.967679, -4.102850"
    )

    static let colegioLastrilla = BusStop(
        id: "colegio-lastrilla",
        name: "Colegio",
        area: "La Lastrilla",
        coordinates: "40.966693, -4.102033"
    )

    static let parroquiaSotillo = BusStop(
        id: "parroquia-sotillo",
        name: "Parroquia el Sotillo",
        area: "El Sotillo",
        coordinates: "40.963449, -4.095073"
    )

    static let hotelAvSotillo = BusStop(
        id: "hotel-av-sotillo",
        name: "Hotel Avenida del Sotillo",
        area: "El Sotillo",
        coordinates: "40.965769, -4.097825"
    )

    static let maspalomas = BusStop(
        id: "maspalomas",
        name: "Calle Maspalomas",
        area: "El Sotillo",
        coordinates: "40.965714, -4.094892"
    )

    static let centroBoal = BusStop(
        id: "centro-boal",
        name: "Centro Cultural Julio Boal",
        area: "El Sotillo",
        coordinates: "40.967592, -4.091377"
    )

    static let paseoCabanillasSotillo = BusStop(
        id: "paseo-cabanillas-sotillo",
        name: "Colegio Madres Concepcionistas",
        area: "El Sotillo",
        coordinates: "40.962806, -4.092689"
    )

    static let rafaelLasHeras = BusStop(
        id: "rafael-las-heras",
        name: "Rafael de las Heras",
        area: "El Sotillo",
        coordinates: "40.961939, -4.096711"
    )

    static let ventaMagullo = BusStop(
        id: "venta-magullo",
        name: "Venta Magullo",
        area: "El Sotillo",
        coordinates: "40.960876, -4.100906"
    )

    // MARK: - M5 (Pirón Valley)

    static let tizneros = BusStop(
        id: "tizneros",
        name: "Tizneros",
        area: "Tizneros",
        coordinates: "40.991521, -4.055240"
    )

    static let espirdo = BusStop(
        id: "espirdo",
        name: "Espirdo",
        area: "Espirdo",
        coordinates: "40.996957, -4.073623"
    )

    static let laHiguera = BusStop(
        id: "la-higuera",
        name: "La Higuera",
        area: "La Higuera",
        coordinates: "41.016117, -4.080770"
    )

    static let brieva = BusStop(
        id: "brieva",
        name: "Brieva",
        area: "Brieva",
        coordinates: "41.035677, -4.052387"
    )

    static let basardilla = BusStop(
        id: "basardilla",
        name: "Basardilla",
        area: "Basardilla",
        coordinates: "41.027220, -4.025058"
    )

    static let stoDomingoPiron = BusStop(
        id: "sto-domingo-piron",
        name: "Sto. Domingo de Pirón",
        area: "Sto. Domingo de Pirón",
        coordinates: "41.041438, -3.989562"
    )

    // MARK: - M6 / M7 Shared – San Cristóbal cluster

    static let sanCristobal = BusStop(
        id: "san-cristobal",
        name: "San Cristóbal de Segovia",
        area: "San Cristóbal de Segovia",
        coordinates: "40.952056, -4.081139"
    )

    static let sanCristobalIglesia = BusStop(
        id: "san-cristobal-iglesia",
        name: "Iglesia",
        area: "San Cristóbal de Segovia",
        coordinates: "40.951733, -4.077499"
    )

    static let sanCristobalRotonda = BusStop(
        id: "san-cristobal-rotonda",
        name: "Rotonda",
        area: "San Cristóbal de Segovia",
        coordinates: "40.951224, -4.073449"
    )

    // MARK: - M6 / M7 Shared – Palazuelos / Tabanera

    static let palazuelos = BusStop(
        id: "palazuelos",
        name: "Palazuelos",
        area: "Palazuelos",
        coordinates: "40.931068, -4.064340"
    )

    static let palazuelosColegio = BusStop(
        id: "palazuelos-colegio",
        name: "Colegio",
        area: "Palazuelos",
        coordinates: "40.933921, -4.063495"
    )

    static let tabanera = BusStop(
        id: "tabanera",
        name: "Tabanera",
        area: "Tabanera",
        coordinates: "40.934336, -4.067014"
    )

    static let tabanera2 = BusStop(
        id: "tabanera-2",
        name: "Tabanera 2",
        area: "Tabanera",
        coordinates: "40.937491, -4.065818"
    )

    // MARK: - M6 / M7 Shared – Sonsoto → Torrecaballeros

    static let sonsoto = BusStop(
        id: "sonsoto",
        name: "Potro",
        area: "Sonsoto",
        coordinates: "40.954774, -4.040524"
    )

    static let sonsoto2 = BusStop(
        id: "sonsoto-2",
        name: "Sonsoto 2",
        area: "Sonsoto",
        details: "Junto a C/ Peñas lisas",
        coordinates: "40.957470, -4.039154"
    )

    static let trescasas = BusStop(
        id: "trescasas",
        name: "Plaza de la constitución",
        area: "Trescasas",
        coordinates: "40.961834, -4.037367"
    )

    static let trescasas2 = BusStop(
        id: "trescasas-2",
        name: "Trescasas 2",
        area: "Trescasas",
        coordinates: "40.963899, -4.034776"
    )

    static let cabanillas = BusStop(
        id: "cabanillas",
        name: "Cabanillas",
        area: "Cabanillas",
        coordinates: "40.974402, -4.028241"
    )

    static let torrecaballeros = BusStop(
        id: "torrecaballeros",
        name: "Torrecaballeros",
        area: "Torrecaballeros",
        coordinates: "40.991880, -4.022848"
    )

    static let torrecaballeros2 = BusStop(
        id: "torrecaballeros-2",
        name: "Torrecaballeros 2",
        area: "Torrecaballeros",
        details: "Junto a la taberna del Rancho",
        coordinates: "40.995364, -4.021688"
    )

    static let torrecaballeros3 = BusStop(
        id: "torrecaballeros-3",
        name: "Torrecaballeros 3",
        area: "Torrecaballeros",
        details: "En carretera hacia Turégano",
        coordinates: "40.999144, -4.020855"
    )

    // MARK: - M8 (La Granja / Valsaín)

    static let penasDelErizo = BusStop(
        id: "penas-del-erizo",
        name: "Peñas del Erizo",
        area: "La Granja",
        coordinates: "40.914155, -4.066063"
    )

    static let cLaFuencisla = BusStop(
        id: "c-la-fuencisla",
        name: "C. La Fuencisla",
        area: "La Granja",
        coordinates: "40.917151, -4.060857"
    )

    static let fabricaCristal = BusStop(
        id: "fabrica-cristal",
        name: "Fábrica Cristal",
        area: "La Granja",
        coordinates: "40.902789, -4.006773"
    )

    static let piscinas = BusStop(
        id: "piscinas",
        name: "Piscinas",
        area: "La Granja",
        coordinates: "40.909126, -4.009200"
    )

    static let ptasSegovia = BusStop(
        id: "ptas-segovia",
        name: "Ptas. Segovia",
        area: "La Granja",
        coordinates: "40.900325, -4.009467"
    )

    static let laPradera = BusStop(
        id: "la-pradera",
        name: "La Pradera",
        area: "Valsaín",
        coordinates: "40.878114, -4.018356"
    )

    static let fronton = BusStop(
        id: "fronton",
        name: "Frontón",
        area: "Valsaín",
        coordinates: "40.875741, -4.021955"
    )

    static let plazaValsain = BusStop(
        id: "plaza-valsain",
        name: "Plaza",
        area: "Valsaín",
        coordinates: "40.878374, -4.027028"
    )
}
