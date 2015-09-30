Feature: Bruke Altinn formidlingstjeneste til å finne nye meldinger og lagre i sak/arkivløsningen

  Scenario: Altinn har nye meldinger
    Given Altinn har melding til mottaker 910094092 som mottaker finner
    When mottaker sjekker etter nye meldinger
    Then mottaker mottar liste over nye meldinger

  Scenario: Altinn har ikke nye meldinger
    Given Altinn har ikke melding til mottaker 810076402
    When mottaker sjekker etter nye meldinger
    Then mottaker mottar tom liste

  Scenario Outline: Mottar arkivspesifikk melding
    Given ny <meldingsformat> melding
    When mottaker mottar melding
    Then arkivsystem skal lagre melding
  Examples:
    |meldingsformat|
    |360           |
    |eforte        |
    |akos          |
