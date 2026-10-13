const typeColors={word:'#6488b8',sheet:'#619276',pdf:'#bf8173',fig:'#9783b4',image:'#b49b6b',md:'#799397',zip:'#8d96a3',other:'#88978c'};
function folderIcon(c='#86a8dc',size=''){return `<svg class="folder-icon ${size}" viewBox="0 0 48 38" style="color:${c}" aria-hidden="true"><path d="M2 7a4 4 0 0 1 4-4h12l5 5h18a4 4 0 0 1 4 4v20a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4z" opacity=".48"/><path d="M2 14a4 4 0 0 1 4-4h35a4 4 0 0 1 4 4v18a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4z"/></svg>`}

export function fileIcon(f,size=''){
 if(f.type==='folder')return folderIcon(f.color||'#a4b9ac',size==='large'?'big':'');
 const c=typeColors[f.type]||typeColors.other;
 const letter=({word:'W',sheet:'X',pdf:'P',fig:'F',md:'M',zip:'Z',image:'',other:'D'})[f.type]??'D';
 const glyph=f.type==='image'?'<path d="M10 18h11v9H10zM10 25l3-3 3 3 2-2 3 3M18 20h.01" fill="none" stroke="white" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>':`<text x="15.5" y="25.5" text-anchor="middle" fill="white" font-family="Arial,sans-serif" font-size="10" font-weight="700">${letter}</text>`;
 return `<span class="filetype ${size}" style="--file-color:${c}" aria-hidden="true"><svg viewBox="0 0 32 38"><path d="M7 2h13l7 7v26a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Z" fill="${c}" fill-opacity=".08" stroke="${c}" stroke-opacity=".38" stroke-width="1"/><path d="M20 2v7h7" fill="${c}" fill-opacity=".15" stroke="${c}" stroke-opacity=".38" stroke-width="1"/><path d="M11 12h9" stroke="${c}" stroke-opacity=".4" stroke-width="1"/><rect x="4" y="15" width="23" height="16" rx="3" fill="${c}"/>${glyph}</svg></span>`;
}
