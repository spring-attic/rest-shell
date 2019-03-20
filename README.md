# Spring HATEOAS-compliant REST shell

# Installing the binaries

Using Homebrew:

		brew install rest-shell

Download the binary .tar.gz package:

[https://github.com/spring-projects/rest-shell/downloads](https://github.com/spring-projects/rest-shell/downloads)

		tar -zxvf rest-shell-1.2.0.RELEASE.tar.gz
		cd rest-shell-1.2.0.RELEASE
		bin/rest-shell

# Building and Running

		git clone git://github.com/spring-projects/rest-shell.git
		cd rest-shell
		./gradlew installApp
		cd build/install/rest-shell-1.2.0.RELEASE
		bin/rest-shell

# Getting Started

This project is a command-line shell that aims to make writing REST-based applications easier. It is based on [spring-shell](https://github.com/springsource/spring-shell) and integrated with [Spring HATEOAS](https://github.com/springsource/spring-hateoas) in such a way that REST resources that output JSON compliant with Spring HATEOAS can be discovered by the shell and interactions with the REST resources become much easier than by manipulating the URLs in bash using a tool like `curl`.

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

_NOTE: If you want tab completion of discovered rels, just use the `--rel` flag._

### Creating new resources

The rest-shell can do basic parsing of JSON data within the shell (though there are some limitations due to the nature of the command line parsing being sensitive to whitespace). This makes it easy to create new resources by including JSON data directly in the shell:

		http://localhost:8080/person:> post --data "{name: 'John Doe'}"
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
	128 items uploaded to the server using POST.
	http://localhost:8080/person:>

You can also reference a specific file rather than an entire directory.

	http://localhost:8080/person:> post --from work/people_to_load/someone.json
	1 items uploaded to the server using POST.
	http://localhost:8080/person:>

### Passing query parameters

If you're calling URLs that require query parameters, you'll need to pass those as a JSON-like fragment in the `--params` parameter to the `get` and `list` commands. Here's an example of calling a URL that expects parameter input:

		http://localhost:8080/person:> get search/byName --params "{name: 'John Doe'}"

### Outputing results to a file

It's not always desirable to output the results of an HTTP request to the screen. It's handy for debugging but sometimes you want to save the results of a request because they're not easily reproducible or any number of other equally valid reasons. All the HTTP commands take an `--output` parameter that writes the results of an HTTP operation to the given file. For example, to output the above search to a file:

		http://localhost:8080/person:> get search/byName --params "{name: 'John Doe'}" --output by_name.txt
		>> by_name.txt
		http://localhost:8080/person:>

### Sending complex JSON

Because the `rest-shell` uses the [spring-shell](https://github.com/springsource/spring-shell) underneath, there are limitations on the format of the JSON data you can enter directly into the command line. If your JSON is too complex for the simplistic limitations of the shell `--data` parameter, you can simply load the JSON from a file or from all the files in a directory.

When doing a `post` or `put`, you can optionally pass the `--from` parameter. The value of this parameter should either be a file or a directory. If the value is a directory, the shell will read each file that ends with `.json` and make a POST or PUT with the contents of that file. If the parameter is a file, then the `rest-shell` will simpy load that file and POST/PUT that data in that individual file.

### Shelling out to bash

One of the nice things about spring-shell is that you can directly shell out commands to the underlying terminal shell. This is useful for doing things like load a JSON file in an editor. For instance, assume I have the Sublime Text 2 command `subl` in my path. I can then load a JSON file for editing from the rest-shell like this:

		http://localhost:8080/person:> ! subl test.json
		http://localhost:8080/person:>

I then edit the file as I wish. When I'm ready to POST that data to the server, I can do so using the `--from` parameter:

		http://localhost:8080/person:> post --from test.json
		1 items uploaded to the server using POST.
		http://localhost:8080/person:>

### Setting context variables

Starting with rest-shell version 1.1, you can also work with context variables during your shell session. This is useful for saving settings you might reference often. The rest-shell now integrates Spring Expression Language support, so these context variables are usable in expressions within the shell.

##### Working with variables

		http://localhost:8080/person:> var set --name specialUri --value http://longdomainname.com/api
		http://localhost:8080/person:> var get --name specialUri
		http://longdomainname.com/api
		http://localhost:8080/person:> var list
		{
			"responseHeaders" : {
				... HTTP headers from last request
			},
			"responseBody" : {
				... Body from the last request
			},
			"specialUri" : "http://longdomainname.com/api",
			"requestUrl" : ... URL from the last request,
			"env" : {
				... System properties and environment variables
			}
		}

The variables are accessible from SpEL expressions which are valid in a number of different contexts, most importantly in the `path` argument to the HTTP and discover commands, and in the `data` argument to the `put` and `post` commands.

Since the rest-shell is aware of environment variables and system properties, you can incorporate external parameters into your interaction with the shell. For example, to externally define a baseUri, you could set a system property before invoking the shell. The shell will incorporate anything defined in the `JAVA_OPTS` environment variable, so you could parameterize your interaction with a REST service.

		JAVA_OPTS="-DbaseUri=https://mylongdomain.com/api" rest-shell

		http://localhost:8080:> discover #{env.baseUri}
		rel                href
		=================================================================
		... resources for this URL
		https://mylongdomain.com/api:>

### Per-user shell initialization

The rest-shell supports a "dotrc" type of initialization by reading in all files found in the `$HOME/.rest-shell/` directory and assuming they have shell commands in them. The rest-shell will execute these commands on startup. This makes it easy to set variables for commonly-used URIs or possibly set a `baseUri`.

		echo "var set --name svcuri --value https://api.myservice.com/v1" > ~/.rest-shell/00-vars
		echo "discover #{svcuri}" > ~/.rest-shell/01-baseUri

		> rest-shell

		INFO: No resources found...
		INFO: Base URI set to 'https://api.myservice.com/v1'

		 ___ ___  __ _____  __  _  _     _ _  __
		| _ \ __/' _/_   _/' _/| || |   / / | \ \
		| v / _|`._`. | | `._`.| >< |  / / /   > >
		|_|_\___|___/ |_| |___/|_||_| |_/_/   /_/
		1.2.0.RELEASE

		Welcome to the REST shell. For assistance hit TAB or type "help".
		https://api.myservice.com/v1:>

### SSL Certificate Validation

If you generate a self-signed certificate for your server, by default the rest-shell will complain and refuse to connect. This is the default behavior of RestTemplate. To turn off certificate and hostname checking, use the `ssl validate --enabled false` command.

### HTTP Basic authentication

There is also a convenience command for setting an HTTP Basic authentication header. Use `auth basic --username user --password passwd` to set a username and password to base64 encode and place into the Authorization header that will be part of the current session's headers.

You can clear the authentication by using the `auth clear` command or by removing the Authorization header using the `headers clear` command.

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
* `var clear` - Clear this shell's variable context.
* `var get` - Get a variable in this shell's context.
* `var list` - List variables currently set in this shell's context.
* `var set` - Set a variable in this shell's context.
* `up` - Traverse one level up in the URL hierarchy.
* `get` - HTTP GET from the given path.
* `post` - HTTP POST to the given path, passing JSON given in the `--data` parameter.
* `put` - HTTP PUT to the given path, passing JSON given in the `--data` parameter.
* `delete` - HTTP DELETE to the given path.
* `auth basic` - Set an HTTP Basic authentication token for use in this session.
* `auth clear` - Clear the Authorization header currently in use.
* `ssl validate` - Disable certificate checking to work with self-signed certificates.
