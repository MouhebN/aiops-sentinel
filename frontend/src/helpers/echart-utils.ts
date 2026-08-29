import { CallbackDataParams } from 'echarts/types/dist/shared.js';
import 'index.css';
export const tooltipFormatterList = (params: CallbackDataParams[]) => {
  const result = params.map((param) => {
    return {
      value: param.value,
      label: param.name,
      seriesName: param.seriesName,
      color: param.color,
    };
  });
  const tooltipItem = result
    .map((el, index: number) => {
      const displayValue =
        el.seriesName === 'orders' ? `$${el.value}` : `${el.seriesName}: ${el.value}`;

      return `<h6 style="font-size: 12px; margin: 0; font-weight: 600;" ${
        (result.length === 1 || index > 0) && 'mb-0'
      }">

   ${displayValue}</h6>`;
    })
    .join('');

  return `<div style="position:relative; padding:10px; margin:0; color:#fff; box-shadow:none; border-radius:10px;">
            ${tooltipItem}
            <b style="position:absolute; background:#333752; z-index:-10000; bottom:-5px; border-top:none; border-left:none; left:40%; width:15px; height:15px; transform:rotate(45deg); border-right:none; border-bottom:none;"></b>
          </div>`;
};
