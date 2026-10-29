import { useEffect, useRef } from 'react';
import JsBarcode from 'jsbarcode';

interface SerialBarcodeProps {
  value: string;
}

// CODE128 only covers ASCII, so anything else falls back to a dash
const canEncode = (value: string) => /^[\x20-\x7E]+$/.test(value);

// Dark bars on white with the serial number printed underneath, like the receipt label,
// so the drawn barcode can still be scanned from the screen
export const SerialBarcode = ({ value }: SerialBarcodeProps) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const encodable = canEncode(value);

  useEffect(() => {
    if (!encodable || !svgRef.current) return;
    JsBarcode(svgRef.current, value, {
      format: 'CODE128',
      width: 1,
      height: 28,
      margin: 4,
      displayValue: true,
      text: value,
      font: 'monospace',
      fontOptions: 'bold',
      fontSize: 11,
      textMargin: 2,
      background: '#FFFFFF',
      lineColor: '#000000',
    });
  }, [value, encodable]);

  if (!encodable) {
    return <span className="text-[10px] text-[#737887] font-normal">&mdash;</span>;
  }

  return <svg ref={svgRef} className="block rounded-sm" role="img" aria-label={value} />;
};
