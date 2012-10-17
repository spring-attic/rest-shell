# Spring HATEOAS-compliant REST shell

# Installing the binaries

Download the binary .tar.gz package:

[https://github.com/jbrisbin/rest-shell/downloads](https://github.com/jbrisbin/rest-shell/downloads)

		tar -zxvf rest-shell-1.0.0.RELEASE.tar.gz
		cd rest-shell-1.0.0.RELEASE
		bin/rest-shell

# Building and Running

		git clone git://github.com/jbrisbin/rest-shell.git
		cd rest-shell
		./gradlew installApp
		cd build/install/rest-shell
		bin/rest-shell

# Getting Started

This project is a command-line shell that aims to make writing REST-based applications easier. It is based on [spring-shell](http://github.com/springsource/spring-shell) and integrated with [Spring HATEOAS](https://github.com/springsource/spring-hateoas) in such a way that REST resources that output JSON compliant with Spring HATEOAS can be discovered by the shell and interactions with the REST resources become much easier than by manipulating the URLs in bash using a tool like `curl`.

The rest-shell provides a number of useful commands for discovering and interacting with REST resources. For example `discover` will discover what resources are available and print out an easily-readable table of rels and URIs that relate to those resources. Once these resources have been discovered, the `rel` of those URIs can be used in place of the URI itself in most operations, thus cutting down on the amount of typing needed to issue HTTP requests to your REST resources.

### Discovering resources

The rest-shell is aimed at making it easier to interact with REST resources by managing the session `baseUri` much like a directory in a filesystem. Whenever resources are `discover`ed, you can then `follow` to a new baseUri, which means you can then use relative URIs. Here's an example of discovering resources, then following a link by referencing its `rel` value, and then using a relative URI to access resources under that new baseUri:

		http://localhost:8080:> discover
		rel                href
		========================================================
		address            http://localhost:8080/address
		family             http://localhost:8080/family
		people             http://localhost:8080/person
		profile            http://localhost:8080/profile

		http://localhost:8080:> follow people
		http://localhost:8080/person:> list
		rel             href
		===================================================
		people.Person    http://localhost:8080/person/1
		people.Person    http://localhost:8080/person/2
		people.search    http://localhost:8080/person/search

		http://localhost:8080/person:> get 1
		> GET http://localhost:8080/person/1

		< 200 OK
		< ETag: "2"
		< Content-Type: application/json
		<
		{
			"links" : [ {
				"rel" : "self",
				"href" : "http://localhost:8080/person/1"
			}, {
				"rel" : "peeps.Person.profiles",
				"href" : "http://localhost:8080/person/1/profiles"
			}, {
				"rel" : "peeps.Person.addresses",
				"href" : "http://localhost:8080/person/1/addresses"
			} ],
			"name" : "John Doe"
		}

### Creating new resources

The rest-shell can do basic parsing of JSON data within the shell (though there are some limitations due to the nature of the command line parsing being sensitive to whitespace). This makes it easy to create new resources by including JSON data directly in the shell:

		http://localhost:8080/person:> post --data "{name:"John Doe"}"
		> POST http://localhost:8080/person/

		< 201 CREATED
		< Location: http://localhost:8080/person/8
		< Content-Length: 0
		<

		http://localhost:8080/person:> get 8
		> GET http://localhost:8080/person/8

		< 200 OK
		< ETag: "0"
		< Content-Type: application/json
		<
		{
			"links" : [ {
				"rel" : "self",
				"href" : "http://localhost:8080/person/8"
			}, {
				"rel" : "people.Person.addresses",
				"href" : "http://localhost:8080/person/8/addresses"
			}, {
				"rel" : "people.Person.profiles",
				"href" : "http://localhost:8080/person/8/profiles"
			} ],
			"name" : "John Doe"
		}

If your needs of representing JSON get more complicated than what the spring-shell interface can handle, you can create a directory somewhere with `.json` files in it, one file per entitiy, and use the `--from` option to the `post` command. This will walk the directory and make a `POST` request for each `.json` file found.

	http://localhost:8080/person:> post --from work/people_to_load
	128 items POSTed to the server.
	http://localhost:8080/person:>

### Passing query parameters

If you're calling URLs that require query parameters, you'll need to pass those as a JSON-like fragment in the `--params` parameter to the `get` and `list` commands. Here's an example of calling a URL that expects parameter input:

		http://localhost:8080/person:> get search/byName --params "{name:"John Doe"}"

### Commands

The rest-shell provides the following commands:

* `discover` - Find out what resources are available at the given URI. If no URI is given, use the baseUri.
* `follow` - Set the baseUri to the URI assigned to this given `rel` but do not discover resources.
* `list` - Discover the resources available at the current baseUri.
* `baseUri` - Set the base URI used for this point forward in the session. Relative URIs will be calculated relative to this setting.
* `headers set` - Set an HTTP header by passing this command a `--name` and `--value` parameter.
* `headers clear` - Clear all HTTP headers set in this session.
* `headers list` - Print out the currently-set HTTP headers for this session.
* `history list` - List the URIs previously set as baseUris during this session.
* `history go` - Jump to a URI by pulling one from the history.
* `up` - Traverse one level up in the URL hierarchy.
* `get` - HTTP GET from the given path.
* `post` - HTTP POST to the given path, passing JSON given in the `--data` parameter.
* `put` - HTTP PUT to the given path, passing JSON given in the `--data` parameter.
* `delete` - HTTP DELETE to the given path.