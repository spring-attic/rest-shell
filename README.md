# Spring HATEOAS-compliant REST shell

This project is a command-line shell that aims to make writing REST-based applications easier. It is based on [spring-shell](http://github.com/springsource/spring-shell) and integrated with [Spring HATEOAS](https://github.com/springsource/spring-hateoas) in such a way that REST resources that output JSON compliant with Spring HATEOAS can be discovered by the shell and interactions with the REST resources become much easier than by manipulating the URLs in bash using a tool like `curl`.

The rest-shell provides a number of useful commands for discovering and interacting with REST resources. For example `discover` will discover what resources are available and print out an easily-readable table of rels and URIs that relate to those resources. Once a resource has been discovered, the `rel` of that URI can be used in place of the URI itself in most operations, thus cutting down on the amount of typing needed to issue HTTP requests to your REST resources.

### Example interaction

An example interaction in the rest-shell might look like:

		> bin/rest-shell

		 ___ ___  __ _____  __  _  _     _ _  __
		| _ \ __/' _/_   _/' _/| || |   / / | \ \
		| v / _|`._`. | | `._`.| >< |  / / /   > >
		|_|_\___|___/ |_| |___/|_||_| |_/_/   /_/

		Welcome to the REST shell. For assistance hit TAB or type "help".
		http://localhost:8080:> discover
		rel                href
		========================================================
		address            http://localhost:8080/address
		family             http://localhost:8080/family
		people             http://localhost:8080/person
		profile            http://localhost:8080/profile

		http://localhost:8080:> follow person
		rel             href
		===================================================
		peeps.Person    http://localhost:8080/people/1
		peeps.Person    http://localhost:8080/people/2
		peeps.Person    http://localhost:8080/people/3
		peeps.search    http://localhost:8080/people/search

		http://localhost:8080/people:> get 1
		> GET http://localhost:8080/people/1

		< 200 OK
		< ETag: "2"
		< Content-Type: application/json
		<
		{
			"links" : [ {
				"rel" : "self",
				"href" : "http://localhost:8080/people/1"
			}, {
				"rel" : "peeps.Person.profiles",
				"href" : "http://localhost:8080/people/1/profiles"
			}, {
				"rel" : "peeps.Person.addresses",
				"href" : "http://localhost:8080/people/1/addresses"
			}, {
				"rel" : "added-link",
				"href" : "http://localhost:8080/people"
			} ],
			"name" : "John Doe"
		}

### Creating new resources

The rest-shell can do basic parsing of JSON data within the shell (though there are some limitations due to the nature of the command line parsing being sensitive to whitespace). This makes it easy to create new resources by including JSON data directly in the shell:

		http://localhost:8080/people:> post --data "{name:"John Doe"}"
		> POST http://localhost:8080/people/

		< 201 CREATED
		< Location: http://localhost:8080/people/8
		< Content-Length: 0
		<

		http://localhost:8080/people:> get 8
		> GET http://localhost:8080/people/8

		< 200 OK
		< ETag: "0"
		< Content-Type: application/json
		<
		{
			"links" : [ {
				"rel" : "self",
				"href" : "http://localhost:8080/people/8"
			}, {
				"rel" : "peeps.Person.addresses",
				"href" : "http://localhost:8080/people/8/addresses"
			}, {
				"rel" : "peeps.Person.profiles",
				"href" : "http://localhost:8080/people/8/profiles"
			}, {
				"rel" : "added-link",
				"href" : "http://localhost:8080/people"
			} ],
			"name" : "John Doe"
		}

### Commands

The rest-shell provides the following commands:

* `discover` - Find out what resources are available at the given URI. If no URI is given, use the baseUri.
* `follow` - Set the baseUri and discover what resources are available that this new URI.
* `baseUri` - Set the base URI used for this point forward in the session. Relative URIs will be calculated relative to this setting.
* `header` - Set an HTTP header by passing this command a `--name` and `--value` parameter.
* `clear-headers` - Clear all HTTP headers set in this session.
* `dump-headers` - Print out the currently-set HTTP headers for this session.
* `get` - HTTP GET from the given path.
* `post` - HTTP POST to the given path, passing JSON given in the `--data` parameter.
* `put` - HTTP PUT to the given path, passing JSON given in the `--data` parameter.
* `delete` - HTTP DELETE to the given path.