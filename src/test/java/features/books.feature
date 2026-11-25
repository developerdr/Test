Feature: Books API

  Background:
    * url baseUrl

  Scenario: Say hello
    Given path 'api', 'hello'
    And param name = 'Karate'
    When method get
    Then status 200
    And match response == { message: 'Hello, Karate!' }

  Scenario: Create, read and delete a book
    Given path 'api', 'books'
    And request { title: 'Clean Code', author: 'Robert C. Martin' }
    When method post
    Then status 201
    And match response == { id: '#number', title: 'Clean Code', author: 'Robert C. Martin' }
    * def bookId = response.id

    Given path 'api', 'books', bookId
    When method get
    Then status 200
    And match response.title == 'Clean Code'

    Given path 'api', 'books'
    When method get
    Then status 200
    And match response contains { id: '#(bookId)', title: 'Clean Code', author: '#string' }

    Given path 'api', 'books', bookId
    When method delete
    Then status 204

    Given path 'api', 'books', bookId
    When method get
    Then status 404

  Scenario: Reject a book without a title
    Given path 'api', 'books'
    And request { author: 'Nobody' }
    When method post
    Then status 400
    And match response.error == 'title is required'
