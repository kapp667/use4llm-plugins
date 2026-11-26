# USE plugins

Plugins for [USE](https://github.com/useocl/use), the UML-based Specification Environment.
This repository continues [useocl/use_plugins](https://github.com/useocl/use_plugins), whose last commit is `3cc4e61` (2021-09-30).

## License

The changes made in this repository after `3cc4e61` are licensed under GPL-3.0-or-later. The full text is in [LICENSE](LICENSE).
`git diff 3cc4e61 HEAD` shows these changes. A file added after `3cc4e61` is covered in full.

This grant does not cover upstream or third-party code:

- useocl/use_plugins declares no license. Its authors have not licensed its files, except the few that carry their own notice, and this repository grants no rights to them.
- The jars under `*/trunk/lib` keep their own licenses.

The following files are under the Eclipse Public License 1.0, which is not compatible with the GPL:

- `XMIHandler/trunk/src/org/tzi/use/plugins/xmihandler/utils/UMLResourcesUtil.java`.
- The EMF and UML2 jars in `XMIHandler/trunk/lib`.
- `ModelValidator/trunk/lib/org.sat4j.core.jar`, the Eclipse Foundation's build of SAT4J, which the ModelValidator build puts into the plugin jar. The build from sat4j.org is offered under the LGPL 2.1 or later and would remove the conflict.
