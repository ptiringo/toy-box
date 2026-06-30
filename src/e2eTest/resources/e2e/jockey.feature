Feature: ジョッキー API（ブラックボックス E2E）

  Background:
    * url baseUrl

  Scenario: 存在しない ID の照会は 404 と RFC9457 problem+json を返す
    Given path 'api', 'jockeys', '00000000-0000-0000-0000-000000000000'
    When method get
    Then status 404
    And match header Content-Type contains 'application/problem+json'
    And match response.type == 'urn:problem-type:jockey-not-found'
    And match response.title == 'Jockey not found'
    And match response.status == 404
    And match response.detail == '指定された ID のジョッキーは存在しません。'
    And match response.jockey_id == '00000000-0000-0000-0000-000000000000'

  Scenario: 登録したジョッキーを ID で照会できる（write→read 往復）
    # 登録（書き込み）
    Given path 'api', 'jockeys'
    And request { first_name: 'Yutaka', last_name: 'Take' }
    When method post
    Then status 201
    And match response.first_name == 'Yutaka'
    And match response.last_name == 'Take'
    And def jockeyId = response.id
    # 照会（実 DB から別リクエストで読み戻す）
    Given path 'api', 'jockeys', jockeyId
    When method get
    Then status 200
    And match response == { id: '#(jockeyId)', first_name: 'Yutaka', last_name: 'Take' }

