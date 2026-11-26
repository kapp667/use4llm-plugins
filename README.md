# USE plugins

Plugins for [USE](https://github.com/useocl/use), the UML-based Specification Environment.
This repository continues [useocl/use_plugins](https://github.com/useocl/use_plugins), whose last commit is `3cc4e61` (2021-09-30).

## License

The changes made in this repository after `3cc4e61` are licensed under the GNU General Public License, version 3 or any later version (GPL-3.0-or-later). The full text is in [LICENSE](LICENSE).

This grant does not cover upstream or third-party code:

- useocl/use_plugins declares no license, and most of its source files carry no license notice. Those files keep the terms of their authors.
- A few source files carry their own notice, such as the GPL version 2 or later. Those notices apply.
- `XMIHandler/trunk/src/org/tzi/use/plugins/xmihandler/utils/UMLResourcesUtil.java` is under the Eclipse Public License 1.0.
- The jars under `*/trunk/lib` keep their own licenses.

Two bundled libraries are under the Eclipse Public License 1.0, which is not compatible with the GPL:

- `ModelValidator/trunk/lib/org.sat4j.core.jar` is the Eclipse Foundation's build of SAT4J. The build from sat4j.org is also offered under the LGPL 2.1 or later.
- `XMIHandler/trunk/lib` holds the Eclipse EMF and UML2 jars.
