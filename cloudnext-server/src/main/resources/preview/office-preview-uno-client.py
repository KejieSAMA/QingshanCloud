"""Minimal LibreOffice UNO client used by the dedicated Office preview worker.

The process is intentionally short lived. LibreOffice itself stays resident while
this client connects through a local-only named pipe for each conversion.
"""

from __future__ import annotations

import argparse
import os
import sys
import traceback


def property_value(name: str, value):
    from com.sun.star.beans import PropertyValue

    prop = PropertyValue()
    prop.Name = name
    prop.Value = value
    return prop


def connect(pipe_name: str):
    import uno

    local_context = uno.getComponentContext()
    resolver = local_context.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local_context
    )
    return resolver.resolve(
        f"uno:pipe,name={pipe_name};urp;StarOffice.ComponentContext"
    )


def probe(pipe_name: str) -> None:
    context = connect(pipe_name)
    if context is None or context.ServiceManager is None:
        raise RuntimeError("UNO probe returned no component context")


def convert(pipe_name: str, input_path: str, output_path: str, filter_name: str) -> None:
    import uno

    context = connect(pipe_name)
    service_manager = context.ServiceManager
    desktop = service_manager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", context
    )
    document = None
    try:
        document = desktop.loadComponentFromURL(
            uno.systemPathToFileUrl(os.path.abspath(input_path)),
            "_blank",
            0,
            (
                property_value("Hidden", True),
                property_value("ReadOnly", True),
                property_value("Silent", True),
                property_value("UpdateDocMode", 0),
                property_value("MacroExecutionMode", 0),
            ),
        )
        if document is None:
            raise RuntimeError("UNO_LOAD_FAILED: LibreOffice did not open the document")
        document.storeToURL(
            uno.systemPathToFileUrl(os.path.abspath(output_path)),
            (
                property_value("FilterName", filter_name),
                property_value("Overwrite", True),
            ),
        )
    finally:
        if document is not None:
            try:
                document.close(True)
            except Exception:
                try:
                    document.dispose()
                except Exception:
                    pass


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    probe_parser = subparsers.add_parser("probe")
    probe_parser.add_argument("--pipe", required=True)
    convert_parser = subparsers.add_parser("convert")
    convert_parser.add_argument("--pipe", required=True)
    convert_parser.add_argument("--input", required=True)
    convert_parser.add_argument("--output", required=True)
    convert_parser.add_argument("--filter", required=True)
    args = parser.parse_args()

    try:
        if args.command == "probe":
            probe(args.pipe)
        else:
            convert(args.pipe, args.input, args.output, args.filter)
        sys.stdout.write("OK\n")
        return 0
    except Exception as error:
        prefix = "UNO_CONNECT_FAILED" if args.command == "probe" else "UNO_CONVERT_FAILED"
        text = f"{type(error).__name__}: {error}"
        if "UNO_LOAD_FAILED" in text:
            prefix = "UNO_LOAD_FAILED"
        sys.stderr.write(f"{prefix}: {text}\n")
        traceback.print_exc(file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
